package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Buyer: review a product and its farmer from a completed purchase (one review for every product bought). */
public class AddReviewFrame extends JFrame {
    private final DefaultTableModel purchases = AppUtil.model("Purchase ID", "Date", "Product", "Farmer");
    private final JTable purchaseTable = AppUtil.table(purchases);
    private final List<int[]> ids = new ArrayList<>();   // per row: purchaseId, productId, farmerId
    private final JComboBox<String> productRating =
        new JComboBox<>(new String[]{"-- Select --", "1", "2", "3", "4", "5"});
    private final JComboBox<String> farmerRating =
        new JComboBox<>(new String[]{"-- Select --", "1", "2", "3", "4", "5"});
    private final JTextArea comment = new JTextArea(4, 25);

    public AddReviewFrame() {
        setTitle("Add Review");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton close = Theme.navButton("Close", Theme.RED);
        close.addActionListener(e -> dispose());
        add(Theme.navbar("Add Review", close), BorderLayout.NORTH);

        JPanel purchasePanel = new JPanel(new BorderLayout());
        purchasePanel.setBorder(BorderFactory.createTitledBorder("Completed Purchases: Items Not Yet Reviewed"));
        purchasePanel.add(new JScrollPane(purchaseTable), BorderLayout.CENTER);

        comment.setLineWrap(true);
        comment.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Your Review"));
        Theme.formRow(form, 0, "Product Rating (1-5)", productRating, false);
        Theme.formRow(form, 1, "Farmer Rating (1-5)", farmerRating, false);
        JScrollPane commentScroll = new JScrollPane(comment);
        commentScroll.setPreferredSize(new Dimension(400, 100));
        Theme.formRow(form, 2, "Comment", commentScroll, true);

        JButton submit = new JButton("Submit Review");
        JButton clear = new JButton("Clear");
        submit.addActionListener(e -> submitReview());
        clear.addActionListener(e -> clearForm());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        buttons.add(submit);
        buttons.add(clear);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);
        Theme.skip(content);
        content.add(purchasePanel, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        add(Theme.padded(content, 22), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        loadItems();
    }

    private void loadItems() {
        purchases.setRowCount(0);
        ids.clear();
        String sql = "SELECT pu.purchaseId, pu.purchaseDate, p.productId, p.productName, f.farmerId, f.name "
                   + "FROM purchase pu JOIN purchaseitem pi ON pi.purchaseId = pu.purchaseId "
                   + "JOIN product p ON p.productId = pi.productId JOIN farmer f ON f.farmerId = p.farmerId "
                   + "WHERE pu.buyerId = ? AND pu.status = 'Completed' "
                   + "AND NOT EXISTS (SELECT 1 FROM review r WHERE r.purchaseId = pu.purchaseId AND r.productId = pi.productId) "
                   + "ORDER BY pu.purchaseDate DESC";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Session.buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(new int[]{rs.getInt(1), rs.getInt(3), rs.getInt(5)});
                    purchases.addRow(new Object[]{rs.getInt(1), AppUtil.when(rs.getTimestamp(2)), rs.getString(4), rs.getString(6)});
                }
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void submitReview() {
        int viewRow = purchaseTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select the item you want to review.");
            return;
        }
        if (productRating.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Choose a product rating from 1 to 5.");
            productRating.requestFocus();
            return;
        }
        if (farmerRating.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Choose a farmer rating from 1 to 5.");
            farmerRating.requestFocus();
            return;
        }
        String text = comment.getText().trim();
        if (text.length() > 500) {
            JOptionPane.showMessageDialog(this, "Comment must be 500 characters or fewer.");
            comment.requestFocus();
            return;
        }
        int[] row = ids.get(purchaseTable.convertRowIndexToModel(viewRow));
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO review (buyerId, farmerId, productId, purchaseId, productRating, "
               + "farmerRating, reviewComment) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, Session.buyerId);
            ps.setInt(2, row[2]);
            ps.setInt(3, row[1]);
            ps.setInt(4, row[0]);
            ps.setInt(5, productRating.getSelectedIndex());
            ps.setInt(6, farmerRating.getSelectedIndex());
            ps.setString(7, text.isEmpty() ? null : text);
            ps.executeUpdate();
            AppUtil.refreshCredit(con, row[2]);   // update the farmer's credit score
            JOptionPane.showMessageDialog(this, "Thank you! Your review was saved.");
            clearForm();
            loadItems();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1) {
                JOptionPane.showMessageDialog(this, "This item has already been reviewed.");
            } else {
                AppUtil.dbError(this, ex);
            }
        }
    }

    private void clearForm() {
        productRating.setSelectedIndex(0);
        farmerRating.setSelectedIndex(0);
        comment.setText("");
    }
}
