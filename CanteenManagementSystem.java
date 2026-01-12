import java.util.*;
import java.sql.*;

class MenuItem {
    private int id;
    private String name;
    private double price;
    private String category;

    public MenuItem(int id, String name, double price, String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }

    @Override
    public String toString() {
        return name + " - rupees" + price + " (" + category + ")";
    }
}

class Order {
    private List<MenuItem> items;
    private double total;

    public Order() {
        items = new ArrayList<>();
        total = 0.0;
    }

    public void addItem(MenuItem item) {
        items.add(item);
        total += item.getPrice();
    }

    public double getTotal() { return total; }
    public List<MenuItem> getItems() { return items; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Order:\n");
        for (MenuItem item : items) {
            sb.append(item.toString()).append("\n");
        }
        sb.append("Total: rupees").append(total);
        return sb.toString();
    }
}

public class CanteenManagementSystem {
    private static List<MenuItem> menu = new ArrayList<>();
    private static Scanner scanner = new Scanner(System.in);
    private static Connection conn;

    public static void main(String[] args) {
        connectToDB();
        loadMenu();
        while (true) {
            showMenu();
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline
            switch (choice) {
                case 1:
                    displayMenu();
                    break;
                case 2:
                    placeOrder();
                    break;
                case 3:
                    viewOrders();
                    break;
                case 4:
                    System.out.println("Exiting...");
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private static void loadMenu() {
        menu.clear();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM menu_items")) {
            while (rs.next()) {
                menu.add(new MenuItem(rs.getInt("id"), rs.getString("name"), rs.getDouble("price"), rs.getString("category")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (menu.isEmpty()) {
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO menu_items (name, price, category) VALUES (?, ?, ?)")) {
                String[][] items = {{"Burger", "5.99", "Main Course"}, {"Pizza", "8.99", "Main Course"}, {"Salad", "4.99", "Appetizer"}, {"Coffee", "2.99", "Beverage"}, {"Cake", "3.99", "Dessert"}};
                for (String[] item : items) {
                    pstmt.setString(1, item[0]);
                    pstmt.setDouble(2, Double.parseDouble(item[1]));
                    pstmt.setString(3, item[2]);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            loadMenu();
        }
    }

    private static void connectToDB() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:canteen.db");
            createTables();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createTables() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS menu_items (id INTEGER PRIMARY KEY, name TEXT, price REAL, category TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY, total REAL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS order_items (order_id INTEGER, menu_item_id INTEGER, FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(menu_item_id) REFERENCES menu_items(id))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void showMenu() {
        System.out.println("\nCanteen Management System");
        System.out.println("1. Display Menu");
        System.out.println("2. Place Order");
        System.out.println("3. View Orders");
        System.out.println("4. Exit");
        System.out.print("Choose an option: ");
    }

    private static void displayMenu() {
        System.out.println("\nMenu:");
        for (int i = 0; i < menu.size(); i++) {
            System.out.println((i + 1) + ". " + menu.get(i));
        }
    }

    private static void placeOrder() {
        displayMenu();
        System.out.println("Enter item numbers to add to order (0 to finish):");
        List<MenuItem> selectedItems = new ArrayList<>();
        while (true) {
            int itemNum = scanner.nextInt();
            if (itemNum == 0) break;
            if (itemNum > 0 && itemNum <= menu.size()) {
                selectedItems.add(menu.get(itemNum - 1));
                System.out.println("Added: " + menu.get(itemNum - 1).getName());
            } else {
                System.out.println("Invalid item number.");
            }
        }
        if (!selectedItems.isEmpty()) {
            double total = 0.0;
            for (MenuItem item : selectedItems) {
                total += item.getPrice();
            }
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO orders (total) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setDouble(1, total);
                pstmt.executeUpdate();
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int orderId = rs.getInt(1);
                    try (PreparedStatement pstmt2 = conn.prepareStatement("INSERT INTO order_items (order_id, menu_item_id) VALUES (?, ?)")) {
                        for (MenuItem item : selectedItems) {
                            pstmt2.setInt(1, orderId);
                            pstmt2.setInt(2, item.getId());
                            pstmt2.executeUpdate();
                        }
                    }
                    System.out.println("Order placed successfully!");
                    StringBuilder sb = new StringBuilder("Order:\n");
                    for (MenuItem item : selectedItems) {
                        sb.append(item.toString()).append("\n");
                    }
                    sb.append("Total: rupees").append(total);
                    System.out.println(sb.toString());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No items added to order.");
        }
    }

    private static void viewOrders() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders")) {
            boolean hasOrders = false;
            while (rs.next()) {
                hasOrders = true;
                int orderId = rs.getInt("id");
                double total = rs.getDouble("total");
                System.out.println("Order " + orderId + ":");
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT m.* FROM menu_items m JOIN order_items oi ON m.id = oi.menu_item_id WHERE oi.order_id = ?")) {
                    pstmt.setInt(1, orderId);
                    ResultSet rs2 = pstmt.executeQuery();
                    while (rs2.next()) {
                        MenuItem item = new MenuItem(rs2.getInt("id"), rs2.getString("name"), rs2.getDouble("price"), rs2.getString("category"));
                        System.out.println(item.toString());
                    }
                }
                System.out.println("Total: rupees" + total);
                System.out.println();
            }
            if (!hasOrders) {
                System.out.println("No orders yet.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}