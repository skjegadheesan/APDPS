package apdps;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;

/**
 * Look of the whole application: colours, fonts and small builders for website-style pages
 * (navigation bar, gradient background, centred white cards, styled buttons and tables).
 * Pages build their components as usual and call Theme.style(...) once at the end.
 */
public final class Theme {
    private Theme() {}

    // ------------------------------------------------------------------ colours
    public static final Color GREEN_DARK = new Color(0x1B5E20);
    public static final Color GREEN = new Color(0x2E7D32);
    public static final Color GREEN_MID = new Color(0x43A047);
    public static final Color GREEN_LIGHT = new Color(0xE8F5E9);
    public static final Color PAGE = new Color(0xEEF4EC);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x1F2933);
    public static final Color MUTED = new Color(0x52606D);
    public static final Color BORDER = new Color(0xAEBBAE);
    public static final Color ZEBRA = new Color(0xF3F8F3);
    public static final Color SELECT = new Color(0xC8E6C9);
    public static final Color BLUE = new Color(0x1565C0);
    public static final Color RED = new Color(0xC62828);
    public static final Color ORANGE = new Color(0xE65100);
    public static final Color GREY = new Color(0x546E7A);

    private static final String SKIP = "theme.skip";
    private static final String BASE = "theme.base";

    // ------------------------------------------------------------------ fonts
    public static final String FAMILY = pickFamily();

    private static String pickFamily() {
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : new String[]{"Segoe UI", "Calibri", "Verdana", "Arial", "DejaVu Sans"}) {
            if (available.contains(name)) {
                return name;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    /** Call once before any window is created: look and feel, default fonts and dialog fonts. */
    public static void install() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // keep the default look and feel
        }
        UIManager.put("nimbusBase", GREEN);
        UIManager.put("nimbusFocus", GREEN_MID);
        UIManager.put("nimbusSelectionBackground", GREEN);
        UIManager.put("defaultFont", new FontUIResource(font(Font.PLAIN, 16)));
        UIManager.put("OptionPane.messageFont", new FontUIResource(font(Font.PLAIN, 17)));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(font(Font.BOLD, 15)));
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("TabbedPane.tabsOpaque", Boolean.TRUE);
        UIManager.put("ToolTip.font", new FontUIResource(font(Font.PLAIN, 14)));
    }

    /** Opens the window maximised, like a web page that fills the screen. */
    public static void fullScreen(JFrame frame) {
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    // ------------------------------------------------------------------ small builders
    public static void skip(JComponent c) {
        c.putClientProperty(SKIP, Boolean.TRUE);
    }

    /** A label with its own colour (not changed by style()). */
    public static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        skip(l);
        return l;
    }

    public static JButton button(String text, Color bg) {
        return button(text, bg, Color.WHITE);
    }

    public static JButton button(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        styleButton(b, bg, fg);
        return b;
    }

    /** Button for the navigation bar (a little lighter than the bar itself). */
    public static JButton navButton(String text, Color bg) {
        JButton b = button(text, bg);
        b.setFont(font(Font.BOLD, 15));
        b.setBorder(new EmptyBorder(8, 18, 8, 18));
        return b;
    }

    private static void styleButton(JButton b, Color bg, Color fg) {
        b.setUI(new BasicButtonUI());
        b.setFont(font(Font.BOLD, 16));
        b.setForeground(fg);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(11, 24, 11, 24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty(BASE, bg);
        for (java.awt.event.MouseListener l : b.getMouseListeners()) {
            if (l instanceof HoverListener) {
                b.removeMouseListener(l);
            }
        }
        b.addMouseListener(new HoverListener(b, bg));
    }

    private static final class HoverListener extends MouseAdapter {
        private final JButton button;
        private final Color base;

        HoverListener(JButton button, Color base) {
            this.button = button;
            this.base = base;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(base.equals(Color.WHITE) ? GREEN_LIGHT : base.darker());
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            button.setBackground(base);
        }
    }

    /** Colour of a button that was created with plain new JButton(text), chosen from its words. */
    private static Color colorFor(String text) {
        String t = text == null ? "" : text.toLowerCase();
        String[] danger = {"logout", "exit", "cancel", "reject", "remove", "deactivate", "delete"};
        for (String w : danger) {
            if (t.contains(w)) {
                return RED;
            }
        }
        String[] neutral = {"clear", "back", "close"};
        for (String w : neutral) {
            if (t.contains(w)) {
                return GREY;
            }
        }
        String[] good = {"add", "save", "submit", "checkout", "create", "login", "register", "approve", "activate",
            "complete"};
        for (String w : good) {
            if (t.contains(w)) {
                return GREEN;
            }
        }
        return BLUE;
    }

    // ------------------------------------------------------------------ page parts
    /** The top navigation bar of every page: brand and page name on the left, components on the right. */
    public static JPanel navbar(String pageName, JComponent... right) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(GREEN_DARK);
        bar.setBorder(new EmptyBorder(12, 28, 12, 28));
        skip(bar);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        left.add(label("APDPS", Font.BOLD, 28, Color.WHITE));
        left.add(label("|", Font.PLAIN, 24, new Color(0x81C784)));
        left.add(label(pageName, Font.PLAIN, 19, new Color(0xE8F5E9)));
        skip(left);
        bar.add(left, BorderLayout.WEST);

        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        r.setOpaque(false);
        for (JComponent c : right) {
            r.add(c);
        }
        skip(r);
        bar.add(r, BorderLayout.EAST);
        return bar;
    }

    /** Green gradient background used behind the cards of the entry pages. */
    public static JPanel gradient() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, GREEN_DARK, getWidth(), getHeight(), new Color(0x4CAF50)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        skip(p);
        return p;
    }

    /** Puts a card in the middle of a gradient background. */
    public static JPanel centered(JComponent card) {
        JPanel p = gradient();
        p.setLayout(new GridBagLayout());
        p.add(card, new GridBagConstraints());
        return p;
    }

    /** A white rounded card with a soft shadow and the given minimum width. */
    public static JPanel card(int minWidth) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fillRoundRect(4, 6, getWidth() - 8, getHeight() - 8, 26, 26);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 8, getHeight() - 8, 26, 26);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.max(d.width, minWidth), d.height);
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(30, 38, 38, 46));
        skip(p);
        return p;
    }

    /** Heading and small description at the top of a card (uses grid row 0 and 1). */
    public static void cardTitle(JPanel card, int columns, String title, String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = columns; c.anchor = GridBagConstraints.WEST;
        card.add(label(title, Font.BOLD, 30, GREEN_DARK), c);
        c.gridy = 1; c.insets = new Insets(4, 0, 18, 0);
        card.add(label(hint, Font.PLAIN, 15, MUTED), c);
    }

    /**
     * Places a label above its field. col/row address a grid of fields: the label is on
     * grid row 2 + row * 2 and the field below it. span = number of columns the field covers.
     */
    public static void place(JPanel p, int col, int row, int span, String text, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col; c.gridy = 2 + row * 2; c.gridwidth = span;
        c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.insets = new Insets(8, col == 0 ? 0 : 12, 3, col == 0 ? 12 : 0);
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 15));
        l.setForeground(TEXT);
        skip(l);
        p.add(l, c);
        c.gridy = 3 + row * 2;
        c.insets = new Insets(0, col == 0 ? 0 : 12, 6, col == 0 ? 12 : 0);
        if (field instanceof JTextField || field instanceof JComboBox) {
            field.setPreferredSize(new Dimension(Math.max(field.getPreferredSize().width, 240), 42));
        }
        p.add(field, c);
    }

    /** A row of equally wide buttons for the bottom of a card. */
    public static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new GridLayout(1, 0, 12, 0));
        row.setOpaque(false);
        skip(row);
        for (JButton b : buttons) {
            row.add(b);
        }
        return row;
    }

    /** One row of a horizontal form: label on the left, field on the right. */
    public static void formRow(JPanel p, int y, String text, JComponent field, boolean grow) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(10, 14, 6, 18);
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 16));
        l.setForeground(GREEN_DARK);
        skip(l);
        p.add(l, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 6, 14);
        if (field instanceof JComboBox) {
            field.setPreferredSize(new Dimension(260, 40));
        }
        p.add(field, c);
    }

    public static void addAt(JPanel card, JComponent c, int row, int span, int topGap) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row; g.gridwidth = span; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        g.insets = new Insets(topGap, 0, 0, 0);
        card.add(c, g);
    }

    /** Adds empty space around the content of a dashboard or page. */
    public static JPanel padded(JComponent content, int gap) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PAGE);
        p.setBorder(new EmptyBorder(gap, gap, gap, gap));
        skip(p);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------ restyle everything in a window
    /** Applies colours and fonts to every component inside the container. */
    public static void style(Container root) {
        for (Component c : root.getComponents()) {
            styleOne(c);
            boolean inner = c instanceof JComboBox || c instanceof JScrollBar || c instanceof JSpinner
                || c instanceof JTableHeader || c.getClass().getName().contains("Divider");
            if (c instanceof Container && !inner) {
                style((Container) c);
            }
        }
        if (root instanceof JComponent) {
            styleOne(root);
        }
    }

    private static boolean skipped(Component c) {
        return c instanceof JComponent && ((JComponent) c).getClientProperty(SKIP) != null;
    }

    private static void styleOne(Component c) {
        if (skipped(c)) {
            return;
        }
        if (c instanceof JButton) {
            JButton b = (JButton) c;
            if (b.getClientProperty(BASE) == null) {
                styleButton(b, colorFor(b.getText()), Color.WHITE);
            }
        } else if (c instanceof JLabel) {
            JLabel l = (JLabel) c;
            if (!skipped(l)) {
                int style = l.getFont().isBold() ? Font.BOLD : Font.PLAIN;
                l.setFont(font(style, Math.max(l.getFont().getSize(), 16)));
                l.setForeground(TEXT);
            }
        } else if (c instanceof JTable) {
            styleTable((JTable) c);
        } else if (c instanceof JTextField || c instanceof JPasswordField) {
            JTextComponent t = (JTextComponent) c;
            t.setFont(font(Font.PLAIN, 17));
            t.setForeground(TEXT);
            t.setBackground(Color.WHITE);
            t.setCaretColor(TEXT);
            t.setBorder(inputBorder());
        } else if (c instanceof JTextArea) {
            JTextArea t = (JTextArea) c;
            t.setFont(font(Font.PLAIN, 16));
            t.setForeground(TEXT);
            t.setBackground(Color.WHITE);
            t.setBorder(new EmptyBorder(8, 10, 8, 10));
        } else if (c instanceof JComboBox) {
            @SuppressWarnings("unchecked")
            JComboBox<Object> cb = (JComboBox<Object>) c;
            cb.setFont(font(Font.PLAIN, 17));
            cb.setForeground(TEXT);
            cb.setBackground(Color.WHITE);
            cb.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean selected, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                    l.setFont(font(Font.PLAIN, 17));
                    l.setBorder(new EmptyBorder(5, 10, 5, 10));
                    l.setForeground(selected ? Color.BLACK : TEXT);
                    l.setBackground(selected ? SELECT : Color.WHITE);
                    return l;
                }
            });
            if (!cb.isPreferredSizeSet()) {
                Dimension d = cb.getPreferredSize();
                cb.setPreferredSize(new Dimension(Math.max(d.width + 20, 150), 40));
            }
        } else if (c instanceof JCheckBox) {
            JCheckBox cb = (JCheckBox) c;
            cb.setFont(font(Font.PLAIN, 16));
            cb.setForeground(TEXT);
            cb.setOpaque(false);
        } else if (c instanceof JTabbedPane) {
            JTabbedPane tp = (JTabbedPane) c;
            tp.setFont(font(Font.BOLD, 17));
            tp.setBackground(PAGE);
            tp.setOpaque(false);
            tp.setUI(new TabsUI());
        } else if (c instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) c;
            sp.setBorder(new LineBorder(BORDER, 1));
            sp.getViewport().setBackground(Color.WHITE);
        } else if (c instanceof JSplitPane) {
            JSplitPane sp = (JSplitPane) c;
            sp.setDividerSize(10);
            sp.setBorder(null);
            sp.setBackground(PAGE);
        } else if (c instanceof JPanel) {
            JPanel p = (JPanel) c;
            if (!skipped(p) && p.isOpaque()) {
                p.setBackground(CARD);
            }
            Border b = p.getBorder();
            if (b instanceof TitledBorder) {
                TitledBorder tb = (TitledBorder) b;
                tb.setTitleFont(font(Font.BOLD, 18));
                tb.setTitleColor(GREEN_DARK);
                tb.setBorder(new LineBorder(BORDER, 1));
            }
        }
    }

    /** Flat green tabs: the selected tab is dark green with white text, the others are light green. */
    private static final class TabsUI extends javax.swing.plaf.basic.BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(11, 26, 11, 26);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 0, 8, 0);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h,
                                          boolean selected) {
            g.setColor(selected ? GREEN : new Color(0xD7E8D8));
            g.fillRect(x, y, w - 4, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h,
                                      boolean selected) {
            // no border
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            g.setColor(GREEN);
            g.fillRect(0, calculateTabAreaHeight(placement, runCount, maxTabHeight) - 4, tabPane.getWidth(), 3);
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index,
                                           Rectangle iconRect, Rectangle textRect, boolean selected) {
            // no focus rectangle
        }

        @Override
        protected void paintText(Graphics g, int placement, Font f, FontMetrics metrics, int index, String title,
                                 Rectangle textRect, boolean selected) {
            g.setFont(font(Font.BOLD, 17));
            g.setColor(selected ? Color.WHITE : TEXT);
            g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }
    }

    private static Border inputBorder() {
        return BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(6, 10, 6, 10));
    }

    // ------------------------------------------------------------------ tables
    /** Sets each column's preferred width from the longest text in it, so words are never cut off. */
    private static void fitColumns(JTable table) {
        javax.swing.table.TableModel model = table.getModel();   // read the model, not the sorted view
        FontMetrics body = table.getFontMetrics(font(Font.PLAIN, 16));
        FontMetrics head = table.getFontMetrics(font(Font.BOLD, 16));
        int columns = Math.min(model.getColumnCount(), table.getColumnModel().getColumnCount());
        Object photoColumn = table.getClientProperty("theme.photoColumn");   // model column that holds thumbnails
        for (int c = 0; c < columns; c++) {
            javax.swing.table.TableColumn col = table.getColumnModel().getColumn(c);
            int m = col.getModelIndex();   // the column may have been moved, so read the model by its own number
            if (photoColumn instanceof Integer && (Integer) photoColumn == m) {
                col.setMinWidth(100);
                col.setPreferredWidth(100);
                col.setMaxWidth(110);
                continue;
            }
            int width = head.stringWidth(String.valueOf(model.getColumnName(m))) + 34;
            for (int r = 0; r < Math.min(model.getRowCount(), 300); r++) {
                Object v = model.getValueAt(r, m);
                if (v != null) {
                    width = Math.max(width, body.stringWidth(v.toString()) + 34);
                }
            }
            col.setMinWidth(60);
            col.setPreferredWidth(Math.min(width, 460));
        }
    }

    public static void styleTable(JTable table) {
        table.setFont(font(Font.PLAIN, 16));
        table.setForeground(TEXT);
        table.setRowHeight(36);
        table.setGridColor(new Color(0xD5DED5));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionBackground(SELECT);
        table.setSelectionForeground(Color.BLACK);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);

        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                setFont(font(Font.PLAIN, 16));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (selected) {
                    setBackground(SELECT);
                    setForeground(Color.BLACK);
                } else {
                    setBackground(row % 2 == 0 ? Color.WHITE : ZEBRA);
                    setForeground(TEXT);
                }
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, cell);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        if (table.getClientProperty("theme.fit") == null) {
            table.putClientProperty("theme.fit", Boolean.TRUE);
            table.getModel().addTableModelListener(e -> fitColumns(table));
        }

        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 42));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                l.setOpaque(true);
                l.setBackground(GREEN);
                l.setForeground(Color.WHITE);
                l.setFont(font(Font.BOLD, 16));
                l.setHorizontalAlignment(SwingConstants.LEFT);
                l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, GREEN_DARK), new EmptyBorder(0, 10, 0, 10)));
                return l;
            }
        });
    }
}
