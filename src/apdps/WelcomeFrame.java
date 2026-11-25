package apdps;

import javax.swing.*;
import java.awt.*;

public class WelcomeFrame extends JFrame {
    public WelcomeFrame(Navigator nav) {
        setTitle("Agricultural Product Direct Purchase System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton navLogin = Theme.navButton("Login", Theme.GREEN);
        JButton navRegister = Theme.navButton("Register", Theme.GREEN);
        JButton navAdmin = Theme.navButton("Admin Login", Theme.GREEN);
        JButton navExit = Theme.navButton("Exit", Theme.RED);
        add(Theme.navbar("Agricultural Product Direct Purchase System", navLogin, navRegister, navAdmin, navExit),
            BorderLayout.NORTH);

        // hero section
        JPanel hero = Theme.gradient();
        hero.setLayout(new BorderLayout());
        JPanel text = new JPanel();
        text.setOpaque(false);
        Theme.skip(text);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel small = Theme.label("WELCOME TO APDPS", Font.BOLD, 18, new Color(0xC8E6C9));
        JLabel headline = Theme.label("Buy Fresh. Directly From Farmers.", Font.BOLD, 54, Color.WHITE);
        JLabel sub = Theme.label("No middlemen. Fair prices. Clear ratings for every farmer.", Font.PLAIN, 24,
            new Color(0xE8F5E9));
        small.setAlignmentX(Component.CENTER_ALIGNMENT);
        headline.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton ctaLogin = Theme.button("Login", Theme.ORANGE);
        JButton ctaRegister = Theme.button("Register", Color.WHITE, Theme.GREEN_DARK);
        JButton ctaAdmin = Theme.button("Admin Login", Theme.GREEN_DARK);
        JPanel cta = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        cta.setOpaque(false);
        Theme.skip(cta);
        cta.add(ctaLogin);
        cta.add(ctaRegister);
        cta.add(ctaAdmin);
        cta.setAlignmentX(Component.CENTER_ALIGNMENT);

        text.add(small);
        text.add(Box.createVerticalStrut(10));
        text.add(headline);
        text.add(Box.createVerticalStrut(14));
        text.add(sub);
        text.add(Box.createVerticalStrut(34));
        text.add(cta);
        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        Theme.skip(middle);
        middle.add(text);
        hero.add(middle, BorderLayout.CENTER);

        JPanel features = new JPanel(new GridLayout(1, 3, 28, 0));
        features.setOpaque(false);
        Theme.skip(features);
        features.setBorder(new javax.swing.border.EmptyBorder(0, 60, 44, 60));
        features.add(feature("For Farmers", "Add your products, set your own price and watch your credit rank grow with every good review."));
        features.add(feature("For Buyers", "Search fresh products, fill your cart, get an invoice and review the farmer after delivery."));
        features.add(feature("Fair and Clear", "Every farmer has a public credit score, so you always know who is reliable."));
        hero.add(features, BorderLayout.SOUTH);
        add(hero, BorderLayout.CENTER);

        navLogin.addActionListener(e -> {
            dispose();
            nav.show(Navigator.LOGIN);
        });
        ctaLogin.addActionListener(e -> {
            dispose();
            nav.show(Navigator.LOGIN);
        });
        navRegister.addActionListener(e -> {
            dispose();
            nav.show(Navigator.REGISTER);
        });
        ctaRegister.addActionListener(e -> {
            dispose();
            nav.show(Navigator.REGISTER);
        });
        navAdmin.addActionListener(e -> {
            dispose();
            nav.show(Navigator.ADMIN_LOGIN);
        });
        ctaAdmin.addActionListener(e -> {
            dispose();
            nav.show(Navigator.ADMIN_LOGIN);
        });
        navExit.addActionListener(e -> System.exit(0));

        Theme.style(getContentPane());
        Theme.fullScreen(this);
    }

    /** One white information card of the home page. */
    private static JPanel feature(String title, String text) {
        JPanel card = Theme.card(0);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new javax.swing.border.EmptyBorder(22, 28, 34, 36));
        card.add(Theme.label(title, Font.BOLD, 22, Theme.GREEN_DARK), BorderLayout.NORTH);
        JTextArea body = new JTextArea(text);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setBorder(null);
        body.setFont(Theme.font(Font.PLAIN, 16));
        body.setForeground(Theme.TEXT);
        Theme.skip(body);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}
