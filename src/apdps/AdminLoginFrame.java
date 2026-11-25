package apdps;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** Administrator login. Admin usernames look like AD001. */
public class AdminLoginFrame extends JFrame {
    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final Navigator nav;

    public AdminLoginFrame(Navigator nav) {
        this.nav = nav;
        setTitle("APDPS - Admin Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton home = Theme.navButton("Home", Theme.GREEN);
        home.addActionListener(e -> {
            dispose();
            nav.show(Navigator.WELCOME);
        });
        add(Theme.navbar("Admin Login", home), BorderLayout.NORTH);

        JPanel card = Theme.card(520);
        card.setLayout(new GridBagLayout());
        Theme.cardTitle(card, 1, "Administrator login", "Admin usernames look like AD001.");
        Theme.place(card, 0, 0, 1, "Admin Username", username);
        Theme.place(card, 0, 1, 1, "Password", password);
        JButton login = Theme.button("Login", Theme.GREEN);
        JButton back = Theme.button("Back", Theme.GREY);
        login.addActionListener(e -> loginAdmin());
        back.addActionListener(e -> {
            dispose();
            nav.show(Navigator.WELCOME);
        });
        Theme.addAt(card, Theme.buttonRow(login, back), 6, 1, 22);
        add(Theme.centered(card), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        getRootPane().setDefaultButton(login);
    }

    private void loginAdmin() {
        String user = username.getText().trim().toUpperCase();
        String pass = new String(password.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password.");
            return;
        }
        if (!user.matches("AD[0-9]{3,}")) {
            JOptionPane.showMessageDialog(this, "Admin username must look like AD001.");
            username.requestFocus();
            return;
        }
        try {
            String error = UserDao.login(user, pass, "Admin");
            if (error != null) {
                JOptionPane.showMessageDialog(this, error);
                return;
            }
            dispose();
            nav.show(Navigator.ADMIN_DASHBOARD);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database connection failed.\n" + ex.getMessage());
        }
    }
}
