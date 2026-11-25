package apdps;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Farmer: add a product together with its opening stock. */
public class AddProductFrame extends JFrame {
    private final JTextField productCode = new JTextField(20);
    private final JTextField productName = new JTextField(20);
    private final JComboBox<String> category = new JComboBox<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private final JComboBox<String> unit = new JComboBox<>(
        new String[]{"-- Select --", "kg", "gram", "litre", "dozen", "piece", "bag"});
    private final JTextField price = new JTextField(20);
    private final JTextField stock = new JTextField("0", 20);
    private final JCheckBox returnEligible = new JCheckBox("Non-perishable (buyer may request a return)");

    public AddProductFrame() {
        setTitle("Add Product");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton close = Theme.navButton("Close", Theme.RED);
        close.addActionListener(e -> dispose());
        add(Theme.navbar("Add Product", close), BorderLayout.NORTH);

        JPanel card = Theme.card(780);
        card.setLayout(new GridBagLayout());
        Theme.cardTitle(card, 2, "Add a new product", "The product is visible to buyers as soon as it is saved.");
        Theme.place(card, 0, 0, 1, "Product Code", productCode);
        Theme.place(card, 1, 0, 1, "Product Name", productName);
        Theme.place(card, 0, 1, 1, "Category", category);
        Theme.place(card, 1, 1, 1, "Unit", unit);
        Theme.place(card, 0, 2, 1, "Price Per Unit (Rs.)", price);
        Theme.place(card, 1, 2, 1, "Opening Stock", stock);
        Theme.place(card, 0, 3, 2, "Return Policy", returnEligible);
        JButton save = Theme.button("Save Product", Theme.GREEN);
        JButton clear = Theme.button("Clear", Theme.GREY);
        save.addActionListener(e -> saveProduct());
        clear.addActionListener(e -> clearForm());
        Theme.addAt(card, Theme.buttonRow(save, clear), 10, 2, 22);
        add(Theme.centered(card), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        loadCategories();
    }

    private void loadCategories() {
        category.removeAllItems();
        categoryIds.clear();
        category.addItem("-- Select --");
        categoryIds.add(0);
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT categoryId, categoryName FROM category ORDER BY categoryName")) {
            while (rs.next()) {
                categoryIds.add(rs.getInt(1));
                category.addItem(rs.getString(2));
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void saveProduct() {
        String code = productCode.getText().trim();
        String name = productName.getText().trim();

        if (code.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Product code and name are required.");
            return;
        }
        if (code.length() > 30 || !code.matches("[A-Za-z0-9_-]+")) {
            JOptionPane.showMessageDialog(this, "Product code: up to 30 letters, digits, '-' or '_'.");
            productCode.requestFocus();
            return;
        }
        if (name.length() > 100) {
            JOptionPane.showMessageDialog(this, "Product name must be 100 characters or fewer.");
            productName.requestFocus();
            return;
        }
        if (category.getSelectedIndex() <= 0) {
            JOptionPane.showMessageDialog(this, "Select a category. (The admin adds categories.)");
            category.requestFocus();
            return;
        }
        if (unit.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Select a unit.");
            unit.requestFocus();
            return;
        }
        BigDecimal priceValue = AppUtil.amount(price.getText().trim());
        if (priceValue == null || priceValue.signum() <= 0) {
            JOptionPane.showMessageDialog(this, "Price must be a number greater than 0 (max 2 decimals).");
            price.requestFocus();
            return;
        }
        BigDecimal stockValue = AppUtil.amount(stock.getText().trim());
        if (stockValue == null) {
            JOptionPane.showMessageDialog(this, "Stock must be a number, 0 or more (max 2 decimals).");
            stock.requestFocus();
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int productId = AppUtil.insertReturningId(con,
                    "INSERT INTO product (productCode, productName, categoryId, farmerId, unit, pricePerUnit, "
                  + "status, returnEligible) VALUES (?, ?, ?, ?, ?, ?, 'Available', ?)", "PRODUCTID",
                    code, name, categoryIds.get(category.getSelectedIndex()), Session.farmerId,
                    (String) unit.getSelectedItem(), priceValue, returnEligible.isSelected() ? 1 : 0);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO stock (productId, quantity) VALUES (?, ?)")) {
                    ps.setInt(1, productId);
                    ps.setBigDecimal(2, stockValue);
                    ps.executeUpdate();
                }
                con.commit();
                JOptionPane.showMessageDialog(this, "Product added.");
                clearForm();
            } catch (SQLException ex) {
                con.rollback();
                if (ex.getErrorCode() == 1) {
                    JOptionPane.showMessageDialog(this, "Product code \"" + code + "\" is already used.");
                } else {
                    AppUtil.dbError(this, ex);
                }
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void clearForm() {
        productCode.setText("");
        productName.setText("");
        category.setSelectedIndex(0);
        unit.setSelectedIndex(0);
        price.setText("");
        stock.setText("0");
        returnEligible.setSelected(false);
        productCode.requestFocus();
    }
}
