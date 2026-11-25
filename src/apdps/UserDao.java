package apdps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Account creation and login. */
public class UserDao {

    /**
     * Creates a user and its profile row and returns the generated username.
     * The username is built here in Java: the prefix (FA, BY or AD) plus the next free number, which is
     * the highest number already used in any username plus one. The number does not come from the
     * database key, so gaps in the database numbering never show up in the usernames.
     */
    public static String register(String role, String name, String contact, String address,
                                  String password) throws SQLException {
        String prefix;
        String table;
        int width;
        if ("Farmer".equals(role)) {
            prefix = "FA";
            table = "farmer";
            width = 6;
        } else if ("Buyer".equals(role)) {
            prefix = "BY";
            table = "buyer";
            width = 6;
        } else if ("Admin".equals(role)) {
            prefix = "AD";
            table = "admin";
            width = 3;
        } else {
            throw new IllegalArgumentException("Unknown role: " + role);
        }

        for (int attempt = 1; ; attempt++) {
            try (Connection con = DBConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    String username = prefix + String.format("%0" + width + "d", nextNumber(con));

                    int userId = AppUtil.insertReturningId(con,
                        "INSERT INTO app_users (username, password, role) VALUES (?, ?, ?)", "USERID",
                        username, password, role);

                    if ("Admin".equals(role)) {
                        try (PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO admin (userId, name) VALUES (?, ?)")) {
                            ps.setInt(1, userId);
                            ps.setString(2, name);
                            ps.executeUpdate();
                        }
                    } else {
                        int profileId = AppUtil.insertReturningId(con,
                            "INSERT INTO " + table + " (userId, name, contactNumber, address) VALUES (?, ?, ?, ?)",
                            table.toUpperCase() + "ID", userId, name, contact, address);
                        if ("Farmer".equals(role)) {
                            AppUtil.refreshCredit(con, profileId);   // creates the farmer's credit row
                        }
                    }
                    con.commit();
                    return username;
                } catch (SQLException ex) {
                    con.rollback();
                    // ORA-00001: another user took the same number a moment ago - try again with the next one
                    if (ex.getErrorCode() == 1 && attempt < 5) {
                        continue;
                    }
                    throw ex;
                } catch (RuntimeException ex) {
                    con.rollback();
                    throw ex;
                }
            }
        }
    }

    /** The next free username number: the highest number in any username (AD001, FA000007 ...) plus one. */
    private static int nextNumber(Connection con) throws SQLException {
        int highest = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT username FROM app_users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString(1);
                if (username.matches("(AD|FA|BY)[0-9]+")) {
                    highest = Math.max(highest, Integer.parseInt(username.substring(2)));
                }
            }
        }
        return highest + 1;
    }

    /**
     * Checks the username, password and role. On success the Session is filled and null is returned;
     * otherwise the message to show the user is returned.
     */
    public static String login(String username, String password, String role) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            int userId;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT userId, password, role, status FROM app_users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()
                            || !password.equals(rs.getString("password"))
                            || !role.equalsIgnoreCase(rs.getString("role"))) {
                        return "Invalid username or password.";
                    }
                    if (!"Active".equalsIgnoreCase(rs.getString("status"))) {
                        return "This account is inactive. Contact the administrator.";
                    }
                    userId = rs.getInt("userId");
                }
            }

            String table = role.equals("Admin") ? "admin" : role.equals("Farmer") ? "farmer" : "buyer";
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT " + table + "Id, name FROM " + table + " WHERE userId = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "Profile not found for this account.";
                    }
                    Session.clear();
                    Session.userId = userId;
                    Session.username = username;
                    Session.role = role;
                    Session.name = rs.getString("name");
                    int profileId = rs.getInt(1);
                    if (role.equals("Admin")) {
                        Session.adminId = profileId;
                    } else if (role.equals("Farmer")) {
                        Session.farmerId = profileId;
                    } else {
                        Session.buyerId = profileId;
                    }
                }
            }
            return null;
        }
    }
}
