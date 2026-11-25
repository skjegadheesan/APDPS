package apdps;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main application class. It is the only class that knows every page: it creates a page when
 * another page asks for it through the Navigator. The pages themselves are independent of each other.
 */
public class APDPSApp implements Navigator {

    public static void main(String[] args) {
        Theme.install();
        SwingUtilities.invokeLater(() -> new APDPSApp().show(WELCOME));
    }

    @Override
    public void show(String screen) {
        JFrame frame;
        switch (screen) {
            case WELCOME:
                frame = new WelcomeFrame(this);
                break;
            case LOGIN:
                frame = new LoginFrame(this);
                break;
            case REGISTER:
                frame = new RegisterFrame(this);
                break;
            case ADMIN_LOGIN:
                frame = new AdminLoginFrame(this);
                break;
            case ADMIN_DASHBOARD:
                frame = new AdminDashboard(this);
                break;
            case FARMER_DASHBOARD:
                frame = new FarmerDashboard(this);
                break;
            case BUYER_DASHBOARD:
                frame = new BuyerDashboard(this);
                break;
            case ADD_PRODUCT:
                frame = new AddProductFrame();
                break;
            case CART:
                frame = new CartFrame();
                break;
            case ADD_REVIEW:
                frame = new AddReviewFrame();
                break;
            case REQUEST_RETURN:
                frame = new RequestReturnFrame();
                break;
            default:
                throw new IllegalArgumentException("Unknown screen: " + screen);
        }
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
