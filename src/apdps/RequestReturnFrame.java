package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Buyer: request a return for a completed, return-eligible item and follow earlier requests. */
public class RequestReturnFrame extends JFrame {
    private final DefaultTableModel eligible =
        AppUtil.model("Purchase ID", "Date", "Product", "Qty", "Subtotal");
    private final DefaultTableModel requests =
        AppUtil.model("Request ID", "Product", "Requested On", "Status");
    private final JTable eligibleTable = AppUtil.table(eligible);
    private final JTable requestTable = AppUtil.table(requests);
    private final List<Integer> itemIds = new ArrayList<>();   // purchaseItemId per row
    private final JTextArea reason = new JTextArea(3, 25);

    public RequestReturnFrame() {
        setTitle("Request Return");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton close = Theme.navButton("Close", Theme.RED);
        close.addActionListener(e -> dispose());
        add(Theme.navbar("Request Return", close), BorderLayout.NORTH);

        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);

        JPanel itemPanel = new JPanel(new BorderLayout());
        itemPanel.setBorder(BorderFactory.createTitledBorder("Items Eligible For Return"));
        itemPanel.add(new JScrollPane(eligibleTable), BorderLayout.CENTER);

        JPanel reasonPanel = new JPanel(new BorderLayout(14, 10));
        reasonPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        reasonPanel.add(new JLabel("Reason:"), BorderLayout.WEST);
        JScrollPane reasonScroll = new JScrollPane(reason);
        reasonScroll.setPreferredSize(new Dimension(400, 90));
        reasonPanel.add(reasonScroll, BorderLayout.CENTER);
        JButton submit = new JButton("Submit Return Request");
        JButton clear = new JButton("Clear");
        submit.addActionListener(e -> submitRequest());
        clear.addActionListener(e -> reason.setText(""));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        buttons.add(submit);
        buttons.add(clear);
        reasonPanel.add(buttons, BorderLayout.SOUTH);
        itemPanel.add(reasonPanel, BorderLayout.SOUTH);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("My Return Requests"));
        requestPanel.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, itemPanel, requestPanel);
        split.setResizeWeight(0.62);
        add(Theme.padded(split, 22), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        loadAll();
    }

    private void loadAll() {
        loadItems();
        try {
            AppUtil.load(requests,
                "SELECT rr.returnRequestId, p.productName, rr.requestDate, rr.status "
              + "FROM returnrequest rr JOIN purchaseitem pi ON pi.purchaseItemId = rr.purchaseItemId "
              + "JOIN product p ON p.productId = pi.productId WHERE rr.buyerId = ? ORDER BY rr.requestDate DESC",
                Session.buyerId);
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void loadItems() {
        eligible.setRowCount(0);
        itemIds.clear();
        String sql = "SELECT pi.purchaseItemId, pu.purchaseId, pu.purchaseDate, p.productName, pi.quantity, pi.subtotal "
                   + "FROM purchaseitem pi JOIN purchase pu ON pu.purchaseId = pi.purchaseId "
                   + "JOIN product p ON p.productId = pi.productId "
                   + "WHERE pu.buyerId = ? AND pu.status = 'Completed' AND p.returnEligible = 1 "
                   + "AND NOT EXISTS (SELECT 1 FROM returnrequest rr WHERE rr.purchaseItemId = pi.purchaseItemId "
                   + "AND rr.status IN ('Pending', 'Approved')) ORDER BY pu.purchaseDate DESC";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Session.buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itemIds.add(rs.getInt(1));
                    eligible.addRow(new Object[]{rs.getInt(2), AppUtil.when(rs.getTimestamp(3)), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6)});
                }
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void submitRequest() {
        String text = reason.getText().trim();
        int viewRow = eligibleTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select the item you want to return.");
            return;
        }
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please give a reason for the return.");
            reason.requestFocus();
            return;
        }
        if (text.length() < 10) {
            JOptionPane.showMessageDialog(this, "Reason must be at least 10 characters.");
            reason.requestFocus();
            return;
        }
        if (text.length() > 255) {
            JOptionPane.showMessageDialog(this, "Reason must be 255 characters or fewer.");
            reason.requestFocus();
            return;
        }
        try {
            AppUtil.update("INSERT INTO returnrequest (purchaseItemId, buyerId, reason) VALUES (?, ?, ?)",
                itemIds.get(eligibleTable.convertRowIndexToModel(viewRow)), Session.buyerId, text);
            JOptionPane.showMessageDialog(this, "Return request submitted. The admin will review it.");
            reason.setText("");
            loadAll();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }
}
