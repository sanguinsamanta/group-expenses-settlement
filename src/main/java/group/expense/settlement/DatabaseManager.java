package group.expense.settlement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    // SQLite JDBC URL pointing to expenses.db in the project root directory
    private static final String DB_URL = "jdbc:sqlite:expenses.db";

    /**
     * Establishes a new database connection and enables foreign key enforcement.
     * SQLite turns foreign keys off by default, so we enable them per connection.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);

        // Enable foreign key constraints for this connection
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        return conn;
    }

    /**
     * Initializes the database by creating necessary tables if they don't already exist.
     * Uses try-with-resources to ensure Connection and Statement are closed automatically.
     */
    public static void initializeDatabase() throws SQLException {
        // Table definition for members
        String createMembersTable = """
            CREATE TABLE IF NOT EXISTS members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            );
            """;

        // Table definition for expenses with foreign key and amount validation
        String createExpensesTable = """
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK(amount > 0),
                payer_id INTEGER NOT NULL REFERENCES members(id)
            );
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Execute SQL table creation scripts
            stmt.execute(createMembersTable);
            stmt.execute(createExpensesTable);
        }
    }

    /**
     * Inserts a new member into the database and returns the generated ID.
     * Propagates SQLException to the caller (e.g. for unique constraint violations).
     */
    public static int addMember(String name) throws SQLException {
        String insertSql = "INSERT INTO members (name) VALUES (?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating member failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Retrieves all members ordered by ID and maps each row to a Member object.
     * Uses try-with-resources to guarantee all database resources are properly closed.
     */
    public static List<Member> getAllMembers() throws SQLException {
        String querySql = "SELECT id, name FROM members ORDER BY id ASC;";
        List<Member> members = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                members.add(new Member(id, name));
            }
        }

        return members;
    }

    /**
     * Inserts a new expense into the database and returns the generated ID.
     * The payer's name is not stored directly in expenses; it references members(id).
     * Propagates SQLException to the caller (e.g. for foreign key or check constraint violations).
     */
    public static int addExpense(String description, int amount, int payerId) throws SQLException {
        String insertSql = "INSERT INTO expenses (description, amount, payer_id) VALUES (?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, description);
            pstmt.setInt(2, amount);
            pstmt.setInt(3, payerId);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating expense failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Retrieves all expenses ordered by ID ascending.
     * Uses an INNER JOIN to retrieve the payer's name from the members table.
     * Maps each row to an Expense object using try-with-resources.
     */
    public static List<Expense> getAllExpenses() throws SQLException {
        String querySql = """
            SELECT e.id, e.description, e.amount, e.payer_id, m.name AS payer_name
            FROM expenses e
            INNER JOIN members m ON e.payer_id = m.id
            ORDER BY e.id ASC;
            """;
        List<Expense> expenses = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String description = rs.getString("description");
                int amount = rs.getInt("amount");
                int payerId = rs.getInt("payer_id");
                String payerName = rs.getString("payer_name");
                expenses.add(new Expense(id, description, amount, payerId, payerName));
            }
        }

        return expenses;
    }
}
