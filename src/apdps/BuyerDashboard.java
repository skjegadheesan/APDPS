package apdps;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import javax.swing.border.EmptyBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BuyerDashboard extends JFrame {
    private final DefaultTableModel orders = AppUtil.model(
        "Purchase ID", "Date", "Product", "Qty", "Subtotal", "Order Status");
    private final DefaultTableModel ranking = AppUtil.model(
        "Rank", "Farmer", "Avg Rating", "Sales", "Cancelled", "Score", "Category");
    private final JTable orderTable = AppUtil.table(orders);
    private final JTable rankingTable = AppUtil.table(ranking);

    /** One product as shown on a card. */
    private static final class ProductCard {
        int id;
        String code, name, category, farmer, unit;
        BigDecimal price, stock, rating;   // rating is null until the product has been reviewed
        ImageIcon photo;       // already shrunk to the card size; null when the product has no photo
        JPanel panel;
    }

    private static final int CARD_W = 250;
    private static final int GAP = 18;
    private final List<ProductCard> cards = new ArrayList<>();
    private final JPanel grid = new GridPanel();
    private ProductCard selected;
    private final JLabel selectedLabel = new JLabel("Click a product to select it.");

    private final JTextField search = new JTextField(15);
    private final JComboBox<String> category = new JComboBox<>();
    private final JTextField quantity = new JTextField("1", 6);
    private final JButton cartButton = new JButton();
    private final Navigator nav;
    private boolean refreshOnReturn = false;

    public BuyerDashboard(Navigator nav) {
        this.nav = nav;
        setTitle("Buyer Dashboard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton refresh = Theme.navButton("Refresh", Theme.GREEN);
        JButton logout = Theme.navButton("Logout", Theme.RED);
        refresh.addActionListener(e -> loadTables());
        logout.addActionListener(e -> {
            Cart.clear();
            Session.clear();
            dispose();
            nav.show(Navigator.WELCOME);
        });
        JLabel who = Theme.label("Welcome, " + Session.name + " (" + Session.username + ")", Font.PLAIN, 17, Color.WHITE);
        add(Theme.navbar("Buyer Dashboard", who, refresh, logout), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Browse Products", browseTab());
        tabs.addTab("My Orders", orderTab());
        JPanel rankPanel = new JPanel(new BorderLayout());
        rankPanel.setBorder(BorderFactory.createTitledBorder("Farmer Credit Ranking"));
        rankPanel.add(new JScrollPane(rankingTable), BorderLayout.CENTER);
        tabs.addTab("Farmer Ranking", rankPanel);
        add(Theme.padded(tabs, 20), BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                updateCartButton();
                if (refreshOnReturn) {
                    refreshOnReturn = false;
                    loadTables();
                }
            }
        });
        Theme.style(getContentPane());
        Theme.fullScreen(this);
        loadCategories();
        loadTables();
        updateCartButton();
    }

    // ---------------------------------------------------------------- browse
    /** Panel that fills the width of the scroll pane and lays the cards out in as many columns as fit. */
    private static final class GridPanel extends JPanel implements Scrollable {
        GridPanel() {
            super(new GridLayout(0, 1, GAP, GAP));
            setOpaque(false);
            setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
        }

        void fitColumns(int width) {
            int cols = Math.max(1, (width - GAP) / (CARD_W + GAP));
            GridLayout layout = (GridLayout) getLayout();
            if (layout.getColumns() != cols) {
                layout.setColumns(cols);
                revalidate();
            }
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(100, r.height - 40); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JPanel browseTab() {
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> loadProducts());
        category.addActionListener(e -> loadProducts());
        JPanel filter = new JPanel();
        filter.add(new JLabel("Search:"));
        filter.add(search);
        filter.add(new JLabel("Category:"));
        filter.add(category);
        filter.add(searchButton);

        JButton add = new JButton("Add to Cart");
        add.addActionListener(e -> addToCart());
        cartButton.addActionListener(e -> openScreen(Navigator.CART));
        JPanel bottom = new JPanel();
        bottom.add(selectedLabel);
        bottom.add(new JLabel("Quantity:"));
        bottom.add(quantity);
        bottom.add(add);
        bottom.add(cartButton);

        JScrollPane scroll = new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ((GridPanel) grid).fitColumns(scroll.getViewport().getWidth());
            }
        });

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Available Products"));
        p.add(filter, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void loadCategories() {
        category.removeAllItems();
        category.addItem("All");
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT categoryName FROM category ORDER BY categoryName")) {
            while (rs.next()) {
                category.addItem(rs.getString(1));
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void loadProducts() {
        String text = search.getText().trim();
        if (text.length() > 100) {
            JOptionPane.showMessageDialog(this, "Search text must be 100 characters or fewer.");
            return;
        }
        // case-insensitive search; % and _ typed by the buyer are searched for literally
        String like = "%" + text.toUpperCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String chosen = category.getSelectedItem() == null ? "All" : (String) category.getSelectedItem();
        int keepSelected = selected == null ? -1 : selected.id;
        cards.clear();
        selected = null;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "SELECT p.productId, p.productCode, p.productName, c.categoryName, f.name, p.unit, "
              + "p.pricePerUnit, s.quantity, "
              + "(SELECT ROUND(AVG(r.productRating), 1) FROM review r WHERE r.productId = p.productId) "
              + "FROM product p "
              + "JOIN category c ON c.categoryId = p.categoryId JOIN farmer f ON f.farmerId = p.farmerId "
              + "JOIN stock s ON s.productId = p.productId JOIN app_users fu ON fu.userId = f.userId "
              + "WHERE p.status = 'Available' AND fu.status = 'Active' AND s.quantity > 0 "
              + "AND (UPPER(p.productName) LIKE ? ESCAPE '\\' OR UPPER(p.productCode) LIKE ? ESCAPE '\\') "
              + "AND (? = 'All' OR c.categoryName = ?) ORDER BY p.productName")) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, chosen);
            ps.setString(4, chosen);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductCard c = new ProductCard();
                    c.id = rs.getInt(1);
                    c.code = rs.getString(2);
                    c.name = rs.getString(3);
                    c.category = rs.getString(4);
                    c.farmer = rs.getString(5);
                    c.unit = rs.getString(6);
                    c.price = rs.getBigDecimal(7);
                    c.stock = rs.getBigDecimal(8);
                    c.rating = rs.getBigDecimal(9);
                    cards.add(c);
                }
            }
            List<Integer> ids = new ArrayList<>();
            for (ProductCard c : cards) {
                ids.add(c.id);
            }
            Map<Integer, ImageIcon> photos = ProductImageDao.loadScaled(ids, CARD_W - 24, 200);
            for (ProductCard c : cards) {
                c.photo = photos.get(c.id);
                if (c.id == keepSelected) {
                    selected = c;
                }
            }
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
        showCards();
    }

    /** Builds one card per product and puts them in the grid. */
    private void showCards() {
        grid.removeAll();
        for (ProductCard c : cards) {
            c.panel = buildCard(c);
            grid.add(c.panel);
        }
        if (cards.isEmpty()) {
            grid.add(Theme.label("No products found.", Font.PLAIN, 18, Theme.MUTED));
        }
        updateSelection();
        grid.revalidate();
        grid.repaint();
    }

    private JPanel buildCard(ProductCard c) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(CARD_W, 430));

        // photo
        JLabel photo = new JLabel("No photo", SwingConstants.CENTER);
        photo.setForeground(Theme.MUTED);
        photo.setPreferredSize(new Dimension(CARD_W - 24, 200));
        if (c.photo != null) {
            photo.setText(null);
            photo.setIcon(c.photo);
        }
        card.add(photo, BorderLayout.NORTH);

        // the details, one per line
        JPanel details = new JPanel(new GridLayout(0, 1, 0, 3));
        details.setOpaque(false);
        details.add(Theme.label(c.name, Font.BOLD, 17, Theme.TEXT));
        details.add(Theme.label("Code: " + c.code, Font.PLAIN, 14, Theme.MUTED));
        details.add(Theme.label("Category: " + c.category, Font.PLAIN, 14, Theme.TEXT));
        details.add(Theme.label("Farmer: " + c.farmer, Font.PLAIN, 14, Theme.TEXT));
        details.add(Theme.label("\u20B9" + c.price.setScale(2, RoundingMode.HALF_UP) + " per " + c.unit, Font.BOLD, 20, Theme.TEXT));
        details.add(Theme.label("Rating: " + (c.rating == null ? "not rated yet" : c.rating.setScale(1, RoundingMode.HALF_UP).toPlainString()),
            Font.PLAIN, 15, Theme.TEXT));
        details.add(Theme.label("In stock: " + c.stock.stripTrailingZeros().toPlainString() + " " + c.unit,
            Font.PLAIN, 14, Theme.TEXT));
        card.add(details, BorderLayout.CENTER);

        // clicking anywhere on the card selects it (a label that had a listener would otherwise swallow the click)
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selected = c;
                updateSelection();
                if (e.getClickCount() == 2) {
                    addToCart();
                }
            }
        };
        listenRecursively(card, click);
        return card;
    }

    private static void listenRecursively(Component c, MouseAdapter listener) {
        c.addMouseListener(listener);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                listenRecursively(child, listener);
            }
        }
    }

    /** Green frame round the selected card, and its name in the bar under the grid. */
    private void updateSelection() {
        for (ProductCard c : cards) {
            boolean on = c == selected;
            c.panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? Theme.GREEN : new Color(0xD5DED5), on ? 3 : 1),
                new EmptyBorder(on ? 9 : 11, on ? 9 : 11, 10, on ? 9 : 11)));
            c.panel.setBackground(on ? Theme.GREEN_LIGHT : Color.WHITE);
        }
        selectedLabel.setText(selected == null ? "Click a product to select it.  " : "Selected: " + selected.name + "   ");
    }

    private void addToCart() {
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a product first.");
            return;
        }
        BigDecimal qty = AppUtil.amount(quantity.getText().trim());
        if (qty == null) {
            JOptionPane.showMessageDialog(this, "Quantity must be a number (max 2 decimals).");
            quantity.requestFocus();
            return;
        }
        if (qty.signum() <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be greater than 0.");
            quantity.requestFocus();
            return;
        }
        int productId = selected.id;
        BigDecimal stock = selected.stock;
        BigDecimal inCart = Cart.qtyOf(productId);
        if (qty.add(inCart).compareTo(stock) > 0) {
            JOptionPane.showMessageDialog(this, "Only " + stock + " in stock"
                + (inCart.signum() > 0 ? " (you already have " + inCart + " in the cart)." : "."));
            quantity.requestFocus();
            return;
        }
        Cart.add(new Cart.Item(productId, selected.name, selected.unit, selected.price, qty));
        updateCartButton();
    }

    private void updateCartButton() {
        cartButton.setText("View Cart (" + Cart.items().size() + ")");
    }

    /** Opens another page through the Navigator and refreshes this dashboard when it is shown again. */
    private void openScreen(String screen) {
        refreshOnReturn = true;
        nav.show(screen);
    }

    // ---------------------------------------------------------------- orders
    private JPanel orderTab() {
        JButton cancel = new JButton("Cancel Order");
        JButton invoice = new JButton("View Invoice");
        JButton review = new JButton("Add Review");
        JButton returnItem = new JButton("Request Return");
        cancel.addActionListener(e -> cancelOrder());
        invoice.addActionListener(e -> showInvoice());
        review.addActionListener(e -> openScreen(Navigator.ADD_REVIEW));
        returnItem.addActionListener(e -> openScreen(Navigator.REQUEST_RETURN));
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(invoice);
        buttons.add(review);
        buttons.add(returnItem);

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("My Orders"));
        p.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void cancelOrder() {
        int id = AppUtil.selectedInt(orderTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Cancel order " + id + "?", "Cancel Order",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (OrderDao.cancel(id, Session.buyerId)) {
                JOptionPane.showMessageDialog(this, "Order cancelled.");
            } else {
                JOptionPane.showMessageDialog(this, "Only your orders with status Ordered can be cancelled.");
            }
            loadTables();
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    private void showInvoice() {
        int id = AppUtil.selectedInt(orderTable, 0);
        if (id < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        try {
            JTextArea area = new JTextArea(OrderDao.invoiceText(id));
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Invoice", JOptionPane.PLAIN_MESSAGE);
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    // ---------------------------------------------------------------- loading
    private void loadTables() {
        loadProducts();
        try {
            AppUtil.load(orders,
                "SELECT pu.purchaseId, pu.purchaseDate, p.productName, pi.quantity, pi.subtotal, pu.status "
              + "FROM purchase pu JOIN purchaseitem pi ON pi.purchaseId = pu.purchaseId "
              + "JOIN product p ON p.productId = pi.productId WHERE pu.buyerId = ? "
              + "ORDER BY pu.purchaseDate DESC, pi.purchaseItemId", Session.buyerId);
            AppUtil.loadRanking(ranking);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database connection failed.\n" + ex.getMessage());
        }
    }
}
