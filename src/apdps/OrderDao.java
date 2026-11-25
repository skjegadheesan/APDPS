package apdps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Database operations for orders: checkout, completing, cancelling, returns and invoices. */
public class OrderDao {

    /**
     * Places the order for everything in the cart in one transaction: purchase, purchaseitem rows,
     * stock reduction and invoice. The new purchase starts as 'Ordered'.
     * Throws IllegalStateException with a readable message when stock is not enough.
     */
    public static int placeOrder(String paymentMode) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                List<Cart.Item> items = Cart.items();
                BigDecimal[] prices = new BigDecimal[items.size()];
                BigDecimal total = BigDecimal.ZERO;

                // lock each stock row, check availability and use the current database price
                int n = 0;
                for (Cart.Item i : items) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT s.quantity, p.pricePerUnit, p.status, u.status FROM stock s "
                          + "JOIN product p ON p.productId = s.productId "
                          + "JOIN farmer f ON f.farmerId = p.farmerId JOIN app_users u ON u.userId = f.userId "
                          + "WHERE s.productId = ? FOR UPDATE OF s.quantity")) {
                        ps.setInt(1, i.productId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || !"Available".equalsIgnoreCase(rs.getString(3))
                                    || !"Active".equalsIgnoreCase(rs.getString(4))) {
                                throw new IllegalStateException(i.name + " is no longer available.");
                            }
                            if (rs.getBigDecimal(1).compareTo(i.qty) < 0) {
                                throw new IllegalStateException("Only " + rs.getBigDecimal(1) + " "
                                    + i.unit + " of " + i.name + " left.");
                            }
                            prices[n] = rs.getBigDecimal(2);
                        }
                    }
                    total = total.add(i.qty.multiply(prices[n]).setScale(2, RoundingMode.HALF_UP));
                    n++;
                }

                int purchaseId = AppUtil.insertReturningId(con,
                    "INSERT INTO purchase (buyerId, totalAmount, paymentMode) VALUES (?, ?, ?)", "PURCHASEID",
                    Session.buyerId, total, paymentMode);

                n = 0;
                for (Cart.Item i : items) {
                    BigDecimal subtotal = i.qty.multiply(prices[n]).setScale(2, RoundingMode.HALF_UP);
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO purchaseitem (purchaseId, productId, quantity, unitPrice, subtotal) "
                          + "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setInt(1, purchaseId);
                        ps.setInt(2, i.productId);
                        ps.setBigDecimal(3, i.qty);
                        ps.setBigDecimal(4, prices[n]);
                        ps.setBigDecimal(5, subtotal);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE stock SET quantity = quantity - ?, lastUpdated = SYSTIMESTAMP WHERE productId = ?")) {
                        ps.setBigDecimal(1, i.qty);
                        ps.setInt(2, i.productId);
                        ps.executeUpdate();
                    }
                    n++;
                }

                // invoice number: INV- plus the next free number (built in Java, not taken from a database key)
                Savepoint beforeInvoice = con.setSavepoint();
                for (int attempt = 1; ; attempt++) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO invoice (invoiceNumber, purchaseId, totalAmount) VALUES (?, ?, ?)")) {
                        ps.setString(1, "INV-" + String.format("%06d", nextInvoiceNumber(con)));
                        ps.setInt(2, purchaseId);
                        ps.setBigDecimal(3, total);
                        ps.executeUpdate();
                        break;
                    } catch (SQLException ex) {
                        // ORA-00001: another order took the same number a moment ago - try the next one
                        if (ex.getErrorCode() == 1 && attempt < 5) {
                            con.rollback(beforeInvoice);
                            continue;
                        }
                        throw ex;
                    }
                }
                con.commit();
                return purchaseId;
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /**
     * Brings the cart in line with the database (nothing is written to the database): removes products that
     * were deleted, made unavailable, belong to a deactivated farmer or are out of stock, lowers a quantity
     * that is more than the stock, and takes over a changed price. Returns a message for every change.
     */
    public static List<String> syncCart() throws SQLException {
        List<String> notes = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT p.pricePerUnit, s.quantity FROM product p "
               + "JOIN farmer f ON f.farmerId = p.farmerId JOIN app_users u ON u.userId = f.userId "
               + "LEFT JOIN stock s ON s.productId = p.productId "
               + "WHERE p.productId = ? AND p.status = 'Available' AND u.status = 'Active'")) {
            Iterator<Cart.Item> it = Cart.items().iterator();
            while (it.hasNext()) {
                Cart.Item i = it.next();
                ps.setInt(1, i.productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        it.remove();
                        notes.add(i.name + " is no longer available and was removed from your cart.");
                        continue;
                    }
                    BigDecimal price = rs.getBigDecimal(1);
                    BigDecimal stock = rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2);
                    if (stock.signum() <= 0) {
                        it.remove();
                        notes.add(i.name + " is out of stock and was removed from your cart.");
                        continue;
                    }
                    if (i.qty.compareTo(stock) > 0) {
                        notes.add("Only " + stock + " " + i.unit + " of " + i.name + " left; the quantity was lowered from "
                            + i.qty + " to " + stock + ".");
                        i.qty = stock;
                    }
                    if (price.compareTo(i.unitPrice) != 0) {
                        notes.add("The price of " + i.name + " changed from Rs. " + i.unitPrice + " to Rs. " + price + ".");
                        i.unitPrice = price;
                    }
                }
            }
        }
        return notes;
    }

    /** The next free invoice number: the highest number in any invoice number (INV-000007 ...) plus one. */
    private static int nextInvoiceNumber(Connection con) throws SQLException {
        int highest = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT invoiceNumber FROM invoice");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String number = rs.getString(1);
                if (number.matches("INV-[0-9]+")) {
                    highest = Math.max(highest, Integer.parseInt(number.substring(4)));
                }
            }
        }
        return highest + 1;
    }

    /** Ordered -> Completed. Returns false if the order was not in the Ordered state. */
    public static boolean complete(int purchaseId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE purchase SET status = 'Completed' WHERE purchaseId = ? AND status = 'Ordered'")) {
                    ps.setInt(1, purchaseId);
                    changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    con.rollback();
                    return false;
                }
                AppUtil.refreshCreditsForPurchase(con, purchaseId);
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /** Puts the quantities of a purchase back into stock. */
    private static void restock(Connection con, int purchaseId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE stock s SET quantity = quantity + (SELECT SUM(pi.quantity) FROM purchaseitem pi "
              + "WHERE pi.purchaseId = ? AND pi.productId = s.productId), lastUpdated = SYSTIMESTAMP "
              + "WHERE s.productId IN (SELECT pi2.productId FROM purchaseitem pi2 WHERE pi2.purchaseId = ?)")) {
            ps.setInt(1, purchaseId);
            ps.setInt(2, purchaseId);
            ps.executeUpdate();
        }
    }

    /**
     * Ordered -> Cancelled and puts the quantities back into stock.
     * buyerId = 0 lets the admin cancel any order; a buyer can cancel only his own.
     */
    public static boolean cancel(int purchaseId, int buyerId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE purchase SET status = 'Cancelled' WHERE purchaseId = ? AND status = 'Ordered' "
                      + "AND (? = 0 OR buyerId = ?)")) {
                    ps.setInt(1, purchaseId);
                    ps.setInt(2, buyerId);
                    ps.setInt(3, buyerId);
                    changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    con.rollback();
                    return false;
                }
                restock(con, purchaseId);
                AppUtil.refreshCreditsForPurchase(con, purchaseId);
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /**
     * Admin decision on a Pending return request. Approving puts the quantity back into stock and
     * marks the purchase 'Returned' once every item of it has an approved return.
     */
    public static boolean processReturn(int requestId, boolean approve, int adminId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE returnrequest SET status = ?, processedDate = SYSTIMESTAMP, processedByAdminId = ? "
                      + "WHERE returnRequestId = ? AND status = 'Pending'")) {
                    ps.setString(1, approve ? "Approved" : "Rejected");
                    ps.setInt(2, adminId);
                    ps.setInt(3, requestId);
                    changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    con.rollback();
                    return false;
                }
                if (approve) {
                    int productId = 0;
                    int purchaseId = 0;
                    BigDecimal qty = BigDecimal.ZERO;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT pi.productId, pi.purchaseId, pi.quantity FROM returnrequest rr "
                          + "JOIN purchaseitem pi ON pi.purchaseItemId = rr.purchaseItemId "
                          + "WHERE rr.returnRequestId = ?")) {
                        ps.setInt(1, requestId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            productId = rs.getInt(1);
                            purchaseId = rs.getInt(2);
                            qty = rs.getBigDecimal(3);
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE stock SET quantity = quantity + ?, lastUpdated = SYSTIMESTAMP WHERE productId = ?")) {
                        ps.setBigDecimal(1, qty);
                        ps.setInt(2, productId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE purchase SET status = 'Returned' WHERE purchaseId = ? "
                          + "AND NOT EXISTS (SELECT 1 FROM purchaseitem pi WHERE pi.purchaseId = ? "
                          + "AND NOT EXISTS (SELECT 1 FROM returnrequest rr WHERE rr.purchaseItemId = pi.purchaseItemId "
                          + "AND rr.status = 'Approved'))")) {
                        ps.setInt(1, purchaseId);
                        ps.setInt(2, purchaseId);
                        ps.executeUpdate();
                    }
                    AppUtil.refreshCreditsForPurchase(con, purchaseId);
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    /** Printable invoice for a purchase. */
    public static String invoiceText(int purchaseId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT i.invoiceNumber, i.invoiceDate, pu.paymentMode, pu.status, pu.totalAmount, b.name "
                  + "FROM invoice i JOIN purchase pu ON pu.purchaseId = i.purchaseId "
                  + "JOIN buyer b ON b.buyerId = pu.buyerId WHERE i.purchaseId = ?")) {
                ps.setInt(1, purchaseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "No invoice found for this purchase.";
                    }
                    sb.append("INVOICE ").append(rs.getString(1)).append("\n");
                    sb.append("Date    : ").append(rs.getTimestamp(2)).append("\n");
                    sb.append("Buyer   : ").append(rs.getString(6)).append("\n");
                    sb.append("Payment : ").append(rs.getString(3)).append("\n");
                    sb.append("Status  : ").append(rs.getString(4)).append("\n\n");
                    sb.append(String.format("%-22s %8s %-6s %9s %10s%n", "Product", "Qty", "Unit", "Price", "Amount"));
                    sb.append("-----------------------------------------------------------\n");
                    BigDecimal total = rs.getBigDecimal(5);
                    try (PreparedStatement items = con.prepareStatement(
                            "SELECT p.productName, pi.quantity, p.unit, pi.unitPrice, pi.subtotal "
                          + "FROM purchaseitem pi JOIN product p ON p.productId = pi.productId "
                          + "WHERE pi.purchaseId = ?")) {
                        items.setInt(1, purchaseId);
                        try (ResultSet ir = items.executeQuery()) {
                            while (ir.next()) {
                                sb.append(String.format("%-22s %8s %-6s %9s %10s%n", ir.getString(1),
                                    ir.getBigDecimal(2), ir.getString(3), ir.getBigDecimal(4), ir.getBigDecimal(5)));
                            }
                        }
                    }
                    sb.append("-----------------------------------------------------------\n");
                    sb.append(String.format("%-49s %10s%n", "TOTAL (Rs.)", total));
                }
            }
        }
        return sb.toString();
    }
}
