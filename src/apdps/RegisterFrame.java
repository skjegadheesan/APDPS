package apdps;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** Farmer and buyer registration. The username is generated automatically. */
public class RegisterFrame extends JFrame {
    private final JComboBox<String> role = new JComboBox<>(new String[]{"-- Select --", "Farmer", "Buyer"});
    private final JTextField fullName = new JTextField(18);
    private final JTextField contact = new JTextField(18);
    private final JTextField address = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JPasswordField confirm = new JPasswordField(18);
    private final Navigator nav;

    public RegisterFrame(Navigator nav) {
        this.nav = nav;
        setTitle("APDPS - Registration");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton home = Theme.navButton("Home", Theme.GREEN);
        JButton navLogin = Theme.navButton("Login", Theme.GREEN);
        home.addActionListener(e -> {
            dispose();
            nav.show(Navigator.WELCOME);
        });
        navLogin.addActionListener(e -> {
            dispose();
            nav.show(Navigator.LOGIN);
        });
        add(Theme.navbar("Registration", home, navLogin), BorderLayout.NORTH);

        JPanel card = Theme.card(780);
        card.setLayout(new GridBagLayout());
        Theme.cardTitle(card, 2, "Create your account", "Your username is generated automatically when you register.");
        Theme.place(card, 0, 0, 1, "Register As", role);
        Theme.place(card, 1, 0, 1, "Full Name", fullName);
        Theme.place(card, 0, 1, 1, "Contact Number (10 digits, starts with 6, 7, 8 or 9)", contact);
        Theme.place(card, 1, 1, 1, "Address", address);
        Theme.place(card, 0, 2, 1, "Password (6+ characters, letter and digit)", password);
        Theme.place(card, 1, 2, 1, "Confirm Password", confirm);
        JButton create = Theme.button("Create Account", Theme.GREEN);
        JButton login = Theme.button("Already registered? Login", Theme.BLUE);
        create.addActionListener(e -> registerUser());
        login.addActionListener(e -> {
            dispose();
            nav.show(Navigator.LOGIN);
        });
        Theme.addAt(card, Theme.buttonRow(create, login), 8, 2, 22);
        add(Theme.centered(card), BorderLayout.CENTER);

        Theme.style(getContentPane());
        Theme.fullScreen(this);
    }

    private void registerUser() {
        String name = fullName.getText().trim();
        String phone = contact.getText().trim();
        String addr = address.getText().trim();
        String pass = new String(password.getPassword());
        String confirmPass = new String(confirm.getPassword());

        if (role.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Select Farmer or Buyer.");
            role.requestFocus();
            return;
        }
        if (name.isEmpty() || phone.isEmpty() || addr.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Fill in all fields.");
            return;
        }
        if (name.length() > 100 || !name.matches("[A-Za-z .'-]+")) {
            JOptionPane.showMessageDialog(this, "Full name must contain only letters and spaces (up to 100 characters).");
            fullName.requestFocus();
            return;
        }
        String phoneProblem = phoneError(phone);
        if (phoneProblem != null) {
            JOptionPane.showMessageDialog(this, phoneProblem);
            contact.requestFocus();
            return;
        }
        if (addr.length() > 255) {
            JOptionPane.showMessageDialog(this, "Address must be 255 characters or fewer.");
            address.requestFocus();
            return;
        }
        if (pass.length() < 6 || pass.length() > 50) {
            JOptionPane.showMessageDialog(this, "Password must be 6 to 50 characters.");
            password.requestFocus();
            return;
        }
        if (!pass.matches(".*[A-Za-z].*") || !pass.matches(".*[0-9].*")) {
            JOptionPane.showMessageDialog(this, "Password must contain at least one letter and one digit.");
            password.requestFocus();
            return;
        }
        if (!pass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.");
            confirm.requestFocus();
            return;
        }
        try {
            String username = UserDao.register((String) role.getSelectedItem(), name, phone, addr, pass);
            JOptionPane.showMessageDialog(this,
                "Registration successful!\n\nYour username is: " + username
                + "\nPlease note it down - you need it to log in.");
            dispose();
            nav.show(Navigator.LOGIN);
        } catch (SQLException ex) {
            AppUtil.dbError(this, ex);
        }
    }

    /**
     * Checks the contact number. Returns the message to show, or null when the number is valid.
     * A valid mobile number has 10 digits and starts with 6, 7, 8 or 9.
     */
    static String phoneError(String phone) {
        if (!phone.isEmpty() && Character.isDigit(phone.charAt(0)) && "6789".indexOf(phone.charAt(0)) < 0) {
            return "The number must start with 6, 7, 8 or 9.\nEnter a valid mobile number.";
        }
        if (!phone.matches("[6789][0-9]{9}")) {
            return "Contact number must be exactly 10 digits (digits only).";
        }
        return null;
    }
}
