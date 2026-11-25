package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;

/** Buyer: review the cart and check out. */
public class CartFrame extends JFrame {
    private final DefaultTableModel items = AppUtil.model("Product", "Unit", "Price", "Qty", "Subtotal");
    private final JTable cartTable = AppUtil.table(items);
    private final JLabel total = new JLabel();
    private final JComboBox<String> payment = new JComboBox<>(new String[]{"-- Select --", "Cash", "UPI", "Card"});

    public CartFrame() {
        setTitle("My Cart");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton close = Theme.navButton("Close", Theme.RED);
        close.addActionListener(e -> dispose());
        add(Theme.navbar("My Cart", close), BorderLayout.NORTH);

        JButton remove = new JButton("Remove Selected");
        JButton clear = new JButton("Clear Cart");
        remove.addActionListener(e -> removeItem());
        clear.addActionListener(e -> clearCart());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        top.add(remove);
        top.add(clear);

        JPanel cartPanel = new JPanel(new BorderLayout(0, 10));
        cartPanel.setBorder(BorderFactory.createTitledBorder("Cart Items"));
        cartPanel.add(top, BorderLayout.NORTH);
        cartPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JButton checkout = new JButton("Checkout");
        checkout.addActionListener(e -> checkout());
        Theme.skip(total);
        total.setFont(Theme.font(Font.BOLD, 24));
        total.setForeground(Theme.GREEN_DARK);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
        bottom.add(total);
        bottom.add(new JLabel("Payment Mode:"));
        bottom.add(payment);
        bottom.add(checkout);
        cartPanel.add(bottom, BorderLayout.SOUTH);
        add(Theme.padded(cartPanel, 22), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        refresh();
        SwingUtilities.invokeLater(() -> syncCart(false));
    }

    /**
     * Brings the cart in line with the database and tells the buyer what changed (removed items, new prices).
     * Returns true when something changed.
     */
    private boolean syncCart(boolean beforeCheckout) {
        try {
            java.util.List<String> notes = OrderDao.syncCart();
            refresh();
            if (!notes.isEmpty()) {
                String text = String.join("\n", notes)
                    + (beforeCheckout ? "\n\nPlease check your cart and press Checkout again." : "");
                JOptionPane.showMessageDialog(this, text, "Cart updated", JOptionPane.INFORMATION_MESSAGE);
                return true;
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
        return false;
    }

    private void refresh() {
        items.setRowCount(0);
        for (Cart.Item i : Cart.items()) {
            items.addRow(new Object[]{i.name, i.unit, i.unitPrice, i.qty, i.subtotal()});
        }
        total.setText("Total: Rs. " + Cart.total());
    }

    private void removeItem() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row to remove.");
            return;
        }
        Cart.remove(cartTable.convertRowIndexToModel(row));
        refresh();
    }

    private void clearCart() {
        if (Cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Your cart is already empty.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Remove all items from the cart?",
            "Clear Cart", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            Cart.clear();
            refresh();
        }
    }

    private void checkout() {
        if (Cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Your cart is empty.");
            return;
        }
        if (payment.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Choose a payment mode.");
            payment.requestFocus();
            return;
        }
        if (syncCart(true)) {
            return;
        }
        try {
            int purchaseId = OrderDao.placeOrder((String) payment.getSelectedItem());
            Cart.clear();
            refresh();
            JTextArea area = new JTextArea(OrderDao.invoiceText(purchaseId));
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Order placed",
                JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }
}
