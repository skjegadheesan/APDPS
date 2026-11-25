package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class AdminDashboard extends JFrame {
    private final DefaultTableModel categories = AppUtil.model("No.", "Category", "Description");
    private final DefaultTableModel products = AppUtil.model(
        "ID", "Code", "Product", "Category", "Farmer", "Unit", "Price", "Stock", "Status", "Photo");
    private final DefaultTableModel users = AppUtil.model("User ID", "Username", "Role", "Name", "Status");
    private final DefaultTableModel orders = AppUtil.model(
        "Purchase ID", "Date", "Buyer", "Total", "Payment", "Status");
    private final DefaultTableModel returns = AppUtil.model(
        "Request ID", "Product", "Buyer", "Qty", "Reason", "Requested", "Status");
    private final DefaultTableModel ranking = AppUtil.model(
        "Rank", "Farmer", "Avg Rating", "Sales", "Cancelled", "Score", "Category");

    private final JTable categoryTable = AppUtil.table(categories);
    private final JTable productTable = AppUtil.table(products);
    private final JTable userTable = AppUtil.table(users);
    private final JTable orderTable = AppUtil.table(orders);
    private final JTable returnTable = AppUtil.table(returns);
    private final JTable rankingTable = AppUtil.table(ranking);

    private final JLabel imagePreview = new JLabel("Select a product", SwingConstants.CENTER);

    private final JTextField categoryName = new JTextField(15);
    private final JTextField categoryDescription = new JTextField(25);

    public AdminDashboard(Navigator nav) {
        setTitle("Admin Dashboard");
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
        add(Theme.navbar("Admin Dashboard", who, refresh, logout), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Categories", categoryTab());
        tabs.addTab("Products", productTab());
        tabs.addTab("Users", userTab());
        tabs.addTab("Orders", orderTab());
        tabs.addTab("Returns", returnTab());
        tabs.addTab("Farmer Ranking", panel("Farmer Credit Ranking", rankingTable, null));
        add(Theme.padded(tabs, 20), BorderLayout.CENTER);

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

    // ---------------------------------------------------------------- categories
    private JPanel categoryTab() {
        JButton add = new JButton("Add Category");
        add.addActionListener(e -> addCategory());
        JPanel form = new JPanel();
        form.add(new JLabel("Name:"));
        form.add(categoryName);
        form.add(new JLabel("Description:"));
        form.add(categoryDescription);
        form.add(add);
        return panel("Product Categories", categoryTable, form);
    }

    private void addCategory() {
        String name = categoryName.getText().trim();
        String desc = categoryDescription.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Category name is required.");
            categoryName.requestFocus();
            return;
        }
        if (name.length() > 100 || !name.matches("[A-Za-z0-9 &'-]+")) {
            JOptionPane.showMessageDialog(this, "Category name: up to 100 letters, digits, spaces, & ' or -.");
            categoryName.requestFocus();
            return;
        }
        if (desc.length() > 255) {
            JOptionPane.showMessageDialog(this, "Description must be 255 characters or fewer.");
            categoryDescription.requestFocus();
            return;
        }
        try {
            AppUtil.update("INSERT INTO category (categoryName, description) VALUES (?, ?)",
                name, desc.isEmpty() ? null : desc);
            categoryName.setText("");
            categoryDescription.setText("");
            loadTables();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1) {
                JOptionPane.showMessageDialog(this, "Category \"" + name + "\" already exists.");
            } else {
                AppUtil.dbError(this, ex);
            }
        }
    }

    // ---------------------------------------------------------------- products
    private JPanel productTab() {
        JButton toggle = new JButton("Make Available / Unavailable");
        toggle.addActionListener(e -> toggleProduct());
        JButton upload = new JButton("Upload / Change Photo");
        upload.addActionListener(e -> uploadImage());
        JButton removeImage = new JButton("Remove Photo");
        removeImage.addActionListener(e -> removeImage());
        JButton delete = new JButton("Delete Product");
        delete.addActionListener(e -> deleteProduct());
        JPanel buttons = new JPanel();
        buttons.add(toggle);
        buttons.add(upload);
        buttons.add(removeImage);
        buttons.add(delete);

        imagePreview.setPreferredSize(new Dimension(280, 280));
        imagePreview.setBorder(BorderFactory.createTitledBorder("Photo"));
        productTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showPreview();
            }
        });
        JPanel p = panel("All Products", productTable, buttons);
        JPanel side = new JPanel(new BorderLayout());
        side.add(imagePreview, BorderLayout.NORTH);
        p.add(side, BorderLayout.EAST);
        return p;
    }

    /** Shows the stored photo of the selected product on the right of the table. */
    private void showPreview() {
        int id = AppUtil.selectedInt(productTable, 0);
        imagePreview.setIcon(null);
        if (id < 0) {
            imagePreview.setText("Select a product");
            return;
        }
        try {
            BufferedImage image = ProductImageDao.load(id);
            if (image == null) {
                imagePreview.setText("No photo uploaded");
            } else {
                imagePreview.setText(null);
                imagePreview.setIcon(ProductImageDao.fit(image, 250, 250));
            }
        } catch (SQLException ex) {
            imagePreview.setText("Photo could not be loaded");
        }
    }

    /** The admin chooses a picture file; it is stored in the database for the selected product. */
    private void uploadImage() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        String name = AppUtil.selectedText(productTable, 2);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a photo for " + name);
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (JPG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            byte[] jpeg = ProductImageDao.prepare(file);
            ProductImageDao.save(id, file.getName(), jpeg);
            JOptionPane.showMessageDialog(this, "Photo saved for " + name + ".");
            loadTables();
            selectProduct(id);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void removeImage() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        try {
            if (!ProductImageDao.delete(id)) {
                JOptionPane.showMessageDialog(this, "This product has no photo.");
                return;
            }
            loadTables();
            selectProduct(id);
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    /** Selects the row of the given product again after the table was reloaded. */
    private void selectProduct(int id) {
        for (int i = 0; i < products.getRowCount(); i++) {
            if (((Number) products.getValueAt(i, 0)).intValue() == id) {
                int view = productTable.convertRowIndexToView(i);
                productTable.getSelectionModel().setSelectionInterval(view, view);
                return;
            }
        }
    }

    private void toggleProduct() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        try {
            AppUtil.update("UPDATE product SET status = CASE WHEN status = 'Available' THEN 'Unavailable' "
                + "ELSE 'Available' END WHERE productId = ?", id);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    /** Deletes a product after asking the admin for a reason; the farmer is shown the reason at the next login/refresh. */
    private void deleteProduct() {
        int id = AppUtil.selectedInt(productTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        String code = AppUtil.selectedText(productTable, 1);
        String name = AppUtil.selectedText(productTable, 2);

        JTextArea reasonBox = new JTextArea(4, 32);
        reasonBox.setLineWrap(true);
        reasonBox.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(0, 6));
        form.add(new JLabel("Delete product " + name + " (" + code + ")?  Enter the reason for the farmer:"),
            BorderLayout.NORTH);
        form.add(new JScrollPane(reasonBox), BorderLayout.CENTER);

        String reason;
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, "Delete Product", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            reason = reasonBox.getText().trim();
            if (reason.isEmpty()) {
                JOptionPane.showMessageDialog(this, "A reason is required.");
            } else if (reason.length() > 200) {
                JOptionPane.showMessageDialog(this, "The reason must be 200 characters or fewer.");
            } else {
                break;
            }
        }

        try {
            String farmerUsername = null;
            try (java.sql.Connection con = DBConnection.getConnection();
                 java.sql.PreparedStatement ps = con.prepareStatement(
                     "SELECT u.username FROM product p JOIN farmer f ON f.farmerId = p.farmerId "
                   + "JOIN app_users u ON u.userId = f.userId WHERE p.productId = ?")) {
                ps.setInt(1, id);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        farmerUsername = rs.getString(1);
                    }
                }
            }
            if (AppUtil.update("DELETE FROM product WHERE productId = ?", id) == 0) {
                JOptionPane.showMessageDialog(this, "This product no longer exists.");
                loadTables();
                return;
            }
            if (farmerUsername != null) {
                try {
                    Notices.add(farmerUsername, "Your product \"" + name + "\" (" + code
                        + ") was deleted by the admin. Reason: " + reason);
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(this,
                        "The product was deleted, but the message for the farmer could not be saved:\n" + ex.getMessage());
                }
            }
            loadTables();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 2292) {
                JOptionPane.showMessageDialog(this, "This product has orders or reviews, so it cannot be deleted "
                    + "(those records must be kept).\nUse \"Make Available / Unavailable\" to hide it instead.");
            } else {
                AppUtil.dbError(this, ex);
            }
        }
    }

    // ---------------------------------------------------------------- users
    private JPanel userTab() {
        JButton activate = new JButton("Activate");
        JButton deactivate = new JButton("Deactivate");
        JButton newAdmin = new JButton("Create Admin");
        JButton deleteUser = new JButton("Delete User");
        deleteUser.addActionListener(e -> deleteUser());
        activate.addActionListener(e -> setUserStatus("Active"));
        deactivate.addActionListener(e -> setUserStatus("Inactive"));
        newAdmin.addActionListener(e -> createAdmin());
        JPanel buttons = new JPanel();
        buttons.add(activate);
        buttons.add(deactivate);
        buttons.add(newAdmin);
        buttons.add(deleteUser);
        return panel("Registered Users", userTable, buttons);
    }

    private void setUserStatus(String status) {
        int id = AppUtil.selectedInt(userTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        if (id == Session.userId && status.equals("Inactive")) {
            JOptionPane.showMessageDialog(this, "You cannot deactivate your own account.");
            return;
        }
        try {
            AppUtil.update("UPDATE app_users SET status = ? WHERE userId = ?", status, id);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void deleteUser() {
        int id = AppUtil.selectedInt(userTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        if (id == Session.userId) {
            JOptionPane.showMessageDialog(this, "You cannot delete your own account.");
            return;
        }
        String username = AppUtil.selectedText(userTable, 1);
        if (JOptionPane.showConfirmDialog(this, "Delete user " + username + "? This cannot be undone.",
                "Delete User", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            // the profile and credit rows of the user are removed with it (ON DELETE CASCADE)
            AppUtil.update("DELETE FROM app_users WHERE userId = ?", id);
            Notices.take(username);   // drop unread messages so a later user with this username does not get them
            loadTables();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 2292) {
                JOptionPane.showMessageDialog(this, "User " + username + " has products, orders, reviews or "
                    + "returns, so cannot be deleted (those records must be kept).\nUse Deactivate instead.");
            } else {
                AppUtil.dbError(this, ex);
            }
        }
    }

    private void createAdmin() {
        JTextField name = new JTextField(18);
        JPasswordField pass = new JPasswordField(18);
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Full Name:"));
        form.add(name);
        form.add(new JLabel("Password:"));
        form.add(pass);
        if (JOptionPane.showConfirmDialog(this, form, "Create Admin", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        String fullName = name.getText().trim();
        String password = new String(pass.getPassword());
        if (fullName.isEmpty() || !fullName.matches("[A-Za-z .'-]+") || fullName.length() > 100) {
            JOptionPane.showMessageDialog(this, "Enter a valid name (letters and spaces only).");
            return;
        }
        if (password.length() < 6 || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            JOptionPane.showMessageDialog(this, "Password must be 6+ characters with a letter and a digit.");
            return;
        }
        try {
            String username = UserDao.register("Admin", fullName, null, null, password);
            JOptionPane.showMessageDialog(this, "Admin created. Username: " + username);
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    // ---------------------------------------------------------------- orders
    private JPanel orderTab() {
        JButton complete = new JButton("Mark Completed");
        JButton cancel = new JButton("Cancel Order");
        complete.addActionListener(e -> completeOrder());
        cancel.addActionListener(e -> cancelOrder());
        JPanel buttons = new JPanel();
        buttons.add(complete);
        buttons.add(cancel);
        return panel("All Orders (Ordered, Completed, Cancelled, Returned)", orderTable, buttons);
    }

    private void completeOrder() {
        int id = AppUtil.selectedInt(orderTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        try {
            if (!OrderDao.complete(id)) {
                JOptionPane.showMessageDialog(this, "Only orders with status Ordered can be completed.");
            }
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void cancelOrder() {
        int id = AppUtil.selectedInt(orderTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Cancel order " + id + " and restore the stock?",
                "Cancel Order", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (!OrderDao.cancel(id, 0)) {
                JOptionPane.showMessageDialog(this, "Only orders with status Ordered can be cancelled.");
            }
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    // ---------------------------------------------------------------- returns
    private JPanel returnTab() {
        JButton approve = new JButton("Approve Return");
        JButton reject = new JButton("Reject Return");
        approve.addActionListener(e -> decideReturn(true));
        reject.addActionListener(e -> decideReturn(false));
        JPanel buttons = new JPanel();
        buttons.add(approve);
        buttons.add(reject);
        return panel("Return Requests", returnTable, buttons);
    }

    private void decideReturn(boolean approve) {
        int id = AppUtil.selectedInt(returnTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select a return request first.");
            return;
        }
        try {
            if (!OrderDao.processReturn(id, approve, Session.adminId)) {
                JOptionPane.showMessageDialog(this, "Only Pending requests can be processed.");
            }
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    // ---------------------------------------------------------------- loading
    private void loadTables() {
        try {
            AppUtil.load(categories, "SELECT ROW_NUMBER() OVER (ORDER BY categoryName) AS serialNo, categoryName, description "
                + "FROM category ORDER BY categoryName");
            AppUtil.load(products,
                "SELECT p.productId, p.productCode, p.productName, c.categoryName, f.name, p.unit, "
              + "p.pricePerUnit, s.quantity, p.status, "
              + "CASE WHEN EXISTS (SELECT 1 FROM product_image i WHERE i.productId = p.productId) "
              + "THEN 'Yes' ELSE 'No' END FROM product p "
              + "JOIN category c ON c.categoryId = p.categoryId JOIN farmer f ON f.farmerId = p.farmerId "
              + "LEFT JOIN stock s ON s.productId = p.productId ORDER BY p.productId");
            AppUtil.load(users,
                "SELECT u.userId, u.username, u.role, COALESCE(a.name, f.name, b.name), u.status "
              + "FROM app_users u LEFT JOIN admin a ON a.userId = u.userId LEFT JOIN farmer f ON f.userId = u.userId "
              + "LEFT JOIN buyer b ON b.userId = u.userId ORDER BY u.userId");
            AppUtil.load(orders,
                "SELECT pu.purchaseId, pu.purchaseDate, b.name, pu.totalAmount, pu.paymentMode, pu.status "
              + "FROM purchase pu JOIN buyer b ON b.buyerId = pu.buyerId ORDER BY pu.purchaseDate DESC");
            AppUtil.load(returns,
                "SELECT rr.returnRequestId, p.productName, b.name, pi.quantity, rr.reason, rr.requestDate, rr.status "
              + "FROM returnrequest rr JOIN purchaseitem pi ON pi.purchaseItemId = rr.purchaseItemId "
              + "JOIN product p ON p.productId = pi.productId JOIN buyer b ON b.buyerId = rr.buyerId "
              + "ORDER BY rr.requestDate DESC");
            AppUtil.loadRanking(ranking);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database connection failed.\n" + ex.getMessage());
        }
    }
}
