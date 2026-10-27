package group.expense.settlement;

public class Main {

    public static void main(String[] args) {
        try {
            // 1. Initialize SQLite database tables
            DatabaseManager.initializeDatabase();

            // 2. Start the HTTP web server on localhost:8080
            WebServer.startServer();

            // 3. Print success message with local URL
            System.out.println("SortmyExpense application started successfully at http://localhost:8080/");
        } catch (Exception e) {
            // 4. Handle startup exceptions with a concise error message
            System.err.println("Error starting SortmyExpense application: " + e.getMessage());
        }
    }
}
