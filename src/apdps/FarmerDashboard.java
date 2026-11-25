package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;

public class FarmerDashboard extends JFrame {
    private final DefaultTableModel products = AppUtil.model(
        "ID", "Code", "Product", "Category", "Unit", "Price", "Stock", "Status", "Returnable");
    private final DefaultTableModel sales = AppUtil.model(
        "Purchase ID", "Date", "Buyer", "Product", "Qty", "Subtotal", "Order Status");
    private final DefaultTableModel reviews = AppUtil.model(
        "Date", "Buyer", "Product", "Product Rating", "Farmer Rating", "Comment");
    private final JTable productTable = AppUtil.table(products);
    private final JTable salesTable = AppUtil.table(sales);
    private final JTable reviewTable = AppUtil.table(reviews);
    private final JLabel credit = new JLabel();
    private boolean refreshOnReturn = false;

    public FarmerDashboard(Navigator nav) {
        setTitle("Farmer Dashboard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton refresh = Theme.navButton("Refresh", Theme.GREEN);
        JButton logout = Theme.navButton("Logout", Theme.RED);
        refresh.addActionListener(e -> loadTables());
        logout.addActionListener(e -> {
            Session.clear();
            dispose();
            nav.show(Navigator.WELCOME);
        });
        JLabel who = Theme.label("Welcome, " + Session.name + " (" + Session.username + ")", Font.PLAIN, 17, Color.WHITE);
        add(Theme.navbar("Farmer Dashboard", who, refresh, logout), BorderLayout.NORTH);

        Theme.skip(credit);
        credit.setFont(Theme.font(Font.BOLD, 19));
        credit.setForeground(Theme.GREEN_DARK);
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 10));
        info.setBackground(Theme.GREEN_LIGHT);
        info.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        Theme.skip(info);
        info.add(credit);

        JButton add = new JButton("Add Product");
        JButton price = new JButton("Update Price");
        JButton stock = new JButton("Update Stock");
        JButton toggle = new JButton("Make Available / Unavailable");
        add.addActionListener(e -> {
            refreshOnReturn = true;
            nav.show(Navigator.ADD_PRODUCT);
        });
        price.addActionListener(e -> updatePrice());
        stock.addActionListener(e -> updateStock());
        toggle.addActionListener(e -> toggleStatus());
        JPanel productButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        productButtons.add(add);
        productButtons.add(price);
        productButtons.add(stock);
        productButtons.add(toggle);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("My Products", panel("My Products", productTable, productButtons));
        tabs.addTab("My Sales", panel("Orders Containing My Products", salesTable, null));
        tabs.addTab("My Reviews", panel("Buyer Reviews", reviewTable, null));
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        Theme.skip(content);
        content.add(info, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        add(Theme.padded(content, 20), BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                if (refreshOnReturn) {
                    refreshOnReturn = false;
                    loadTables();
                }
            }
        });
        Theme.style(getContentPane());
        Theme.fullScreen(this);
        loadTables();
    }

    private JPanel panel(String title, JTable table, JComponent south) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        if (south != null) {
            p.add(south, BorderLayout.SOUTH);
        }
        return p;
    }

    private void updatePrice() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        String text = JOptionPane.showInputDialog(this, "New price per unit (Rs.):");
        if (text == null) {
            return;
        }
        BigDecimal value = AppUtil.amount(text.trim());
        if (value == null || value.signum() <= 0) {
            JOptionPane.showMessageDialog(this, "Price must be a number greater than 0 (max 2 decimals).");
            return;
        }
        try {
            AppUtil.update("UPDATE product SET pricePerUnit = ? WHERE productId = ? AND farmerId = ?",
                value, id, Session.farmerId);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void updateStock() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        String text = JOptionPane.showInputDialog(this, "New stock quantity:");
        if (text == null) {
            return;
        }
        BigDecimal value = AppUtil.amount(text.trim());
        if (value == null) {
            JOptionPane.showMessageDialog(this, "Stock must be a number, 0 or more (max 2 decimals).");
            return;
        }
        try {
            AppUtil.update("UPDATE stock SET quantity = ?, lastUpdated = SYSTIMESTAMP WHERE productId = ? "
                + "AND productId IN (SELECT productId FROM product WHERE farmerId = ?)", value, id, Session.farmerId);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void toggleStatus() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        try {
            AppUtil.update("UPDATE product SET status = CASE WHEN status = 'Available' THEN 'Unavailable' "
                + "ELSE 'Available' END WHERE productId = ? AND farmerId = ?", id, Session.farmerId);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    /** Shows (once) the messages the admin left for this farmer, e.g. why a product was deleted. */
    private void showNotices() {
        java.util.List<String> messages = Notices.take(Session.username);
        if (messages.isEmpty()) {
            return;
        }
        JTextArea text = new JTextArea(String.join("\n\n", messages), Math.min(10, messages.size() * 3 + 1), 42);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(text), "Message from Admin",
            JOptionPane.WARNING_MESSAGE);
    }

    private void loadTables() {
        SwingUtilities.invokeLater(this::showNotices);
        try {
            AppUtil.load(products,
                "SELECT p.productId, p.productCode, p.productName, c.categoryName, p.unit, p.pricePerUnit, "
              + "s.quantity, p.status, CASE WHEN p.returnEligible = 1 THEN 'Yes' ELSE 'No' END FROM product p "
              + "JOIN category c ON c.categoryId = p.categoryId LEFT JOIN stock s ON s.productId = p.productId "
              + "WHERE p.farmerId = ? ORDER BY p.productId", Session.farmerId);
            AppUtil.load(sales,
                "SELECT pu.purchaseId, pu.purchaseDate, b.name, p.productName, pi.quantity, pi.subtotal, pu.status "
              + "FROM purchaseitem pi JOIN product p ON p.productId = pi.productId "
              + "JOIN purchase pu ON pu.purchaseId = pi.purchaseId JOIN buyer b ON b.buyerId = pu.buyerId "
              + "WHERE p.farmerId = ? ORDER BY pu.purchaseDate DESC", Session.farmerId);
            AppUtil.load(reviews,
                "SELECT r.reviewDate, b.name, p.productName, r.productRating, r.farmerRating, r.reviewComment "
              + "FROM review r JOIN buyer b ON b.buyerId = r.buyerId JOIN product p ON p.productId = r.productId "
              + "WHERE r.farmerId = ? ORDER BY r.reviewDate DESC", Session.farmerId);

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "SELECT creditScore, rankCategory, avgRating FROM farmercredits WHERE farmerId = ?")) {
                ps.setInt(1, Session.farmerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        credit.setText("Your credit score: " + rs.getBigDecimal(1) + "  (" + rs.getString(2)
                            + ")      Average rating: " + rs.getBigDecimal(3));
                    } else {
                        credit.setText("Your credit score: not rated yet");
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database connection failed.\n" + ex.getMessage());
        }
    }
}
