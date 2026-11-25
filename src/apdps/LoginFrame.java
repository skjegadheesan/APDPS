package apdps;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** Farmer and buyer login. The username is the generated one: FA000001 or BY000001. */
public class LoginFrame extends JFrame {
    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final Navigator nav;

    public LoginFrame(Navigator nav) {
        this.nav = nav;
        setTitle("APDPS - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton home = Theme.navButton("Home", Theme.GREEN);
        JButton navRegister = Theme.navButton("Register", Theme.GREEN);
        home.addActionListener(e -> {
            dispose();
            nav.show(Navigator.WELCOME);
        });
        navRegister.addActionListener(e -> {
            dispose();
            nav.show(Navigator.REGISTER);
        });
        add(Theme.navbar("Login", home, navRegister), BorderLayout.NORTH);

        JPanel card = Theme.card(520);
        card.setLayout(new GridBagLayout());
        Theme.cardTitle(card, 1, "Welcome back", "Use your username, for example FA000002 (farmer) or BY000003 (buyer).");
        Theme.place(card, 0, 0, 1, "Username", username);
        Theme.place(card, 0, 1, 1, "Password", password);
        JButton login = Theme.button("Login", Theme.GREEN);
        JButton register = Theme.button("New user? Register", Theme.BLUE);
        login.addActionListener(e -> loginUser());
        register.addActionListener(e -> {
            dispose();
            nav.show(Navigator.REGISTER);
        });
        Theme.addAt(card, Theme.buttonRow(login, register), 6, 1, 22);
        add(Theme.centered(card), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
        getRootPane().setDefaultButton(login);
    }

    private void loginUser() {
        String user = username.getText().trim().toUpperCase();
        String pass = new String(password.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password.");
            return;
        }
        if (user.startsWith("AD")) {
            JOptionPane.showMessageDialog(this, "Administrators must use the Admin Login page.");
            return;
        }
        if (!user.matches("(FA|BY)[0-9]{6}")) {
            JOptionPane.showMessageDialog(this, "Username must look like FA000001 or BY000001.");
            username.requestFocus();
            return;
        }
        String role = user.startsWith("FA") ? "Farmer" : "Buyer";
        try {
            String error = UserDao.login(user, pass, role);
            if (error != null) {
                JOptionPane.showMessageDialog(this, error);
                return;
            }
            dispose();
            nav.show(role.equals("Farmer") ? Navigator.FARMER_DASHBOARD : Navigator.BUYER_DASHBOARD);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database connection failed.\n" + ex.getMessage());
        }
    }
}
