package apdps;

/** Holds the logged-in user while the application is running. */
public class Session {
    public static int userId, adminId, farmerId, buyerId;
    public static String username = "", name = "", role = "";

    public static void clear() {
        userId = adminId = farmerId = buyerId = 0;
        username = name = role = "";
    }
}
