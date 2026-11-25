package apdps;

import java.awt.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Small helpers shared by the pages: table models, simple queries, generated keys and error messages. */
public class AppUtil {
    /** Farmer ranking used by the admin and buyer dashboards (the rank number is added in Java). */
    private static final String RANKING_SQL =
        "SELECT f.name, fc.avgRating, fc.totalSales, fc.cancelledCount, fc.creditScore, fc.rankCategory "
      + "FROM farmercredits fc JOIN farmer f ON f.farmerId = fc.farmerId ORDER BY fc.creditScore DESC, f.name";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        return table;
    }

    /** Value in the given column of the selected row, or null when no row is selected. */
    public static Object selected(JTable table, int column) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return table.getModel().getValueAt(table.convertRowIndexToModel(row), column);
    }

    public static int selectedInt(JTable table, int column) {
        Object value = selected(table, column);
        return value == null ? -1 : ((Number) value).intValue();
    }

    public static String selectedText(JTable table, int column) {
        Object value = selected(table, column);
        return value == null ? null : value.toString();
    }

    /** Sets the parameters of a statement; null values are sent as SQL NULL. */
    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    /** Shows date-time values as 2026-10-18 14:30. */
    private static Object display(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATE_TIME);
        }
        if (value instanceof java.sql.Date) {
            return value.toString();
        }
        if (value != null && value.getClass().getName().startsWith("oracle.sql.")) {
            return value.toString();   // old Oracle drivers return their own date types
        }
        return value;
    }

    /** Runs a SELECT and fills the table model with the rows. */
    public static void load(DefaultTableModel model, String sql, Object... params) throws SQLException {
        model.setRowCount(0);
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] row = new Object[columns];
                    for (int i = 0; i < columns; i++) {
                        row[i] = display(rs.getObject(i + 1));
                    }
                    model.addRow(row);
                }
            }
        }
    }

    /** Runs an INSERT / UPDATE / DELETE and returns the number of rows changed. */
    public static int update(String sql, Object... params) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /**
     * Runs an INSERT on a table whose primary key is an IDENTITY (auto-numbered) column and returns
     * the number that was generated, e.g. insertReturningId(con, sql, "PURCHASEID", params...).
     */
    public static int insertReturningId(Connection con, String sql, String idColumn, Object... params)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{idColumn})) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Loads the farmer ranking: rank 1, 2, 3 ... is counted here in Java, in the order of the credit score. */
    public static void loadRanking(DefaultTableModel model) throws SQLException {
        model.setRowCount(0);
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(RANKING_SQL);
             ResultSet rs = ps.executeQuery()) {
            int rank = 0;
            while (rs.next()) {
                rank++;
                model.addRow(new Object[]{rank, rs.getString(1), rs.getBigDecimal(2), rs.getInt(3),
                    rs.getInt(4), rs.getBigDecimal(5), rs.getString(6)});
            }
        }
    }

    /** Number of purchases with the given status that contain at least one product of the farmer. */
    private static int countPurchases(Connection con, int farmerId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(DISTINCT pu.purchaseId) FROM purchase pu "
              + "JOIN purchaseitem pi ON pi.purchaseId = pu.purchaseId "
              + "JOIN product p ON p.productId = pi.productId WHERE p.farmerId = ? AND pu.status = ?")) {
            ps.setInt(1, farmerId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Credit score (0 to 100), calculated in Java:
     *   rating part      = average rating / 5 x 60
     *   sales part       = completed sales (at most 50) / 50 x 30
     *   reliability part = 10 x completed / (completed + cancelled)
     */
    public static BigDecimal creditScore(BigDecimal avgRating, int sales, int cancelled) {
        BigDecimal ratingPart = avgRating.multiply(BigDecimal.valueOf(12));
        BigDecimal salesPart = BigDecimal.valueOf(Math.min(sales, 50) * 30L)
            .divide(BigDecimal.valueOf(50), 4, RoundingMode.HALF_UP);
        BigDecimal reliability = BigDecimal.ZERO;
        if (sales + cancelled > 0) {
            reliability = BigDecimal.valueOf(10L * sales)
                .divide(BigDecimal.valueOf(sales + cancelled), 4, RoundingMode.HALF_UP);
        }
        return ratingPart.add(salesPart).add(reliability).setScale(2, RoundingMode.HALF_UP);
    }

    /** Platinum 85 or more, Gold 70 or more, Silver 50 or more, otherwise Bronze. */
    public static String rankCategory(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "Platinum";
        }
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "Gold";
        }
        if (score.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "Silver";
        }
        return "Bronze";
    }

    /** Recalculates the credit score of one farmer in Java and saves it in farmercredits. */
    public static void refreshCredit(Connection con, int farmerId) throws SQLException {
        BigDecimal avg = BigDecimal.ZERO;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT AVG(farmerRating) FROM review WHERE farmerId = ?")) {
            ps.setInt(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBigDecimal(1) != null) {
                    avg = rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        int sales = countPurchases(con, farmerId, "Completed");
        int cancelled = countPurchases(con, farmerId, "Cancelled");
        BigDecimal score = creditScore(avg, sales, cancelled);
        String rank = rankCategory(score);

        int changed;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE farmercredits SET avgRating = ?, totalSales = ?, cancelledCount = ?, creditScore = ?, "
              + "rankCategory = ?, lastUpdated = SYSTIMESTAMP WHERE farmerId = ?")) {
            ps.setBigDecimal(1, avg);
            ps.setInt(2, sales);
            ps.setInt(3, cancelled);
            ps.setBigDecimal(4, score);
            ps.setString(5, rank);
            ps.setInt(6, farmerId);
            changed = ps.executeUpdate();
        }
        if (changed == 0) {   // the farmer has no credit row yet
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO farmercredits (farmerId, avgRating, totalSales, cancelledCount, creditScore, rankCategory) "
                  + "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, farmerId);
                ps.setBigDecimal(2, avg);
                ps.setInt(3, sales);
                ps.setInt(4, cancelled);
                ps.setBigDecimal(5, score);
                ps.setString(6, rank);
                ps.executeUpdate();
            }
        }
    }

    /** Recalculates the credit score of every farmer who has a product in the purchase. */
    public static void refreshCreditsForPurchase(Connection con, int purchaseId) throws SQLException {
        List<Integer> farmers = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT DISTINCT p.farmerId FROM purchaseitem pi JOIN product p ON p.productId = pi.productId "
              + "WHERE pi.purchaseId = ?")) {
            ps.setInt(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    farmers.add(rs.getInt(1));
                }
            }
        }
        for (int farmerId : farmers) {
            refreshCredit(con, farmerId);
        }
    }

    /** Date and time as 2026-10-18 14:30 (for values read directly from a ResultSet). */
    public static String when(java.sql.Timestamp t) {
        return t == null ? "" : t.toLocalDateTime().format(DATE_TIME);
    }

    /** Parses a non-negative amount with at most 8 digits and 2 decimals, or returns null. */
    public static BigDecimal amount(String text) {
        if (!text.matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) {
            return null;
        }
        return new BigDecimal(text);
    }

    /** Oracle error codes: ORA-00001 duplicate, ORA-02290 check, ORA-02291 parent missing, ORA-02292 child exists. */
    public static void dbError(Component parent, SQLException ex) {
        String message;
        switch (ex.getErrorCode()) {
            case 1:
                message = "That value already exists.";
                break;
            case 2290:
                message = "A value is outside the allowed range.";
                break;
            case 2291:
                message = "A related record does not exist.";
                break;
            case 2292:
                message = "This record is in use and cannot be removed.";
                break;
            default:
                message = "Database error.\n" + ex.getMessage();
        }
        JOptionPane.showMessageDialog(parent, message);
    }
}
