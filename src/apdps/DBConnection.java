package apdps;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens the database connection. Put your own values below, or set the environment variables
 * APDPS_DB_URL, APDPS_DB_USER and APDPS_DB_PASSWORD (they win over the values below).
 * Never commit real credentials to the repository.
 */
public class DBConnection {

    private static final String URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";
    private static final String USER = "your_user";
    private static final String PASSWORD = "your_password";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            env("APDPS_DB_URL", URL),
            env("APDPS_DB_USER", USER),
            env("APDPS_DB_PASSWORD", PASSWORD));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
