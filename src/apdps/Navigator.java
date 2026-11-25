package apdps;

/**
 * The only thing a page knows about the rest of the application.
 * A page asks the Navigator to show another screen by name; the main application class
 * (APDPSApp) decides which page to create. Pages never create or call each other.
 */
public interface Navigator {
    String WELCOME = "welcome";
    String LOGIN = "login";
    String REGISTER = "register";
    String ADMIN_LOGIN = "adminLogin";
    String ADMIN_DASHBOARD = "adminDashboard";
    String FARMER_DASHBOARD = "farmerDashboard";
    String BUYER_DASHBOARD = "buyerDashboard";
    String ADD_PRODUCT = "addProduct";
    String CART = "cart";
    String ADD_REVIEW = "addReview";
    String REQUEST_RETURN = "requestReturn";

    void show(String screen);
}
