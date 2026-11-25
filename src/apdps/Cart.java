package apdps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** In-memory shopping cart. Rows are written to the database only at checkout. */
public class Cart {
    public static class Item {
        public final int productId;
        public final String name;
        public final String unit;
        public BigDecimal unitPrice;
        public BigDecimal qty;

        public Item(int productId, String name, String unit, BigDecimal unitPrice, BigDecimal qty) {
            this.productId = productId;
            this.name = name;
            this.unit = unit;
            this.unitPrice = unitPrice;
            this.qty = qty;
        }

        public BigDecimal subtotal() {
            return unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static final List<Item> items = new ArrayList<>();

    public static List<Item> items() {
        return items;
    }

    public static boolean isEmpty() {
        return items.isEmpty();
    }

    public static void clear() {
        items.clear();
    }

    public static void remove(int index) {
        items.remove(index);
    }

    public static BigDecimal qtyOf(int productId) {
        for (Item i : items) {
            if (i.productId == productId) {
                return i.qty;
            }
        }
        return BigDecimal.ZERO;
    }

    public static void add(Item item) {
        for (Item i : items) {
            if (i.productId == item.productId) {
                i.qty = i.qty.add(item.qty);
                i.unitPrice = item.unitPrice;   // keep the newest price
                return;
            }
        }
        items.add(item);
    }

    public static BigDecimal total() {
        BigDecimal total = BigDecimal.ZERO;
        for (Item i : items) {
            total = total.add(i.subtotal());
        }
        return total;
    }
}
