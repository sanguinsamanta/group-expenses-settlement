# Group Expense Settlement Management System

A lightweight, full-stack desktop web application designed to track shared group expenses, compute equal fair shares, and calculate direct settlement transfers needed to balance all accounts.

The application features a responsive single-page web interface powered by a pure Java backend using the built-in HTTP server and an embedded SQLite database.

---

## 1. Key Features

- **Member Management**: Add up to 10 group members with unique names and automatic ID assignment.
- **Expense Logging**: Record expenses with descriptions, positive whole-rupee amounts, and payer selection.
- **Real-Time Financial Dashboard**:
  - Total group expenditure.
  - Member count and equal fair share per person.
  - Per-member breakdown showing total paid, equal share, net balance, and status (*Gets Back*, *Owes*, or *Settled*).
- **Automated Settlement Suggestions**: Greedy debtor-creditor matching algorithm that pairs members who owe money with members who are owed money using a two-pointer approach to generate direct, simplified settlement transfers without intermediaries.
- **Paisa-Level Exact Rounding**: Settlement calculations distribute integer remainder paise across members so that balances sum to ₹0.00 without rounding leakage.
- **Embedded Database Persistence**: Stores all members and expenses in an SQLite database file (`expenses.db`).
- **Zero-Dependency Architecture**: Built using pure Java standard library components and SQLite JDBC driver—no external heavy web frameworks required.

---

## 2. Technologies Used

- **Backend**: Java 25 (Standard Edition)
- **HTTP Server**: Java Built-in `com.sun.net.httpserver.HttpServer`
- **Database**: SQLite 3 via `org.xerial:sqlite-jdbc` (version `3.50.3.0`)
- **Build Tool**: Apache Maven (Project Object Model: `pom.xml`)
- **Frontend**: Vanilla HTML5, CSS3, and JavaScript (`fetch` API)
- **Typography**: Google Fonts (*Plus Jakarta Sans*)

---

## 3. Application Architecture & Request Flow

The project follows a layered architecture with clear separation of concerns:

```
[ Web Browser ]
      │  HTTP Requests (HTML / CSS / JS / JSON API)
      ▼
[ WebServer (HttpServer on :8080) ]
   ├── StaticFileHandler (Serves index.html, style.css, script.js from classpath)
   └── API Handlers (/api/members, /api/expenses, /api/summary, /api/settlements)
            │
            ▼
[ SettlementManager ] (Business Logic, Validation, & Settlement Math)
            │
            ▼
[ DatabaseManager ] (JDBC Connection, Prepared Statements, & Foreign Keys)
            │
            ▼
[ SQLite Database (expenses.db) ]
```

### Request Flow
1. **User Action**: The user fills out a form on the web page (e.g., adding an expense) and clicks submit.
2. **Client Validation & Fetch**: JavaScript validates the input fields and sends an asynchronous JSON request using `fetch()` to `/api/expenses`.
3. **Routing & Parsing**: `WebServer` matches the route, validates the JSON payload structure, checks required fields, and extracts parameters.
4. **Business Validation & Calculation**: `SettlementManager` verifies domain rules (e.g., payer exists, limits are respected, amount is positive).
5. **Persistence**: `DatabaseManager` executes a parameterized SQL `INSERT` statement via JDBC into `expenses.db`.
6. **Response & DOM Update**: The server returns an HTTP `201 Created` status with the created entity. The frontend automatically refreshes the tables, summary cards, and settlement list.

---

## 4. Project Folder Structure

```
expense-settler/
│
├── pom.xml                               # Maven project configuration and dependencies
├── expenses.db                           # SQLite database file (created at runtime)
│
├── src/
│   └── main/
│       ├── java/
│       │   └── group/
│       │       └── expense/
│       │           └── settlement/
│       │               ├── Main.java               # Application entry point
│       │               ├── WebServer.java          # HTTP server, routing, JSON parsing
│       │               ├── DatabaseManager.java    # SQLite JDBC connection & CRUD operations
│       │               ├── SettlementManager.java  # Domain logic, summary, settlement algorithm
│       │               ├── Member.java             # Member model class (POJO)
│       │               └── Expense.java            # Expense model class (POJO)
│       │
│       └── resources/
│           └── public/
│               ├── index.html            # Single-page user interface
│               ├── style.css             # UI styling and responsive card layout
│               └── script.js             # Client-side validation, API calls, DOM rendering
│
└── target/                               # Maven compiled classes and packaged JAR
```

---

## 5. Database Schema

The application uses SQLite with foreign-key constraints enabled on every connection (`PRAGMA foreign_keys = ON;`).

### `members` Table
Stores unique names of all participants in the group.

```sql
CREATE TABLE IF NOT EXISTS members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);
```

### `expenses` Table
Stores recorded expenses linked to the member who paid.

```sql
CREATE TABLE IF NOT EXISTS expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    description TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK(amount > 0),
    payer_id INTEGER NOT NULL REFERENCES members(id)
);
```

*Note: The payer's name is dynamically resolved at query time using an `INNER JOIN members ON expenses.payer_id = members.id`.*

---

## 6. REST API Endpoints

All API endpoints exchange data using `Content-Type: application/json; charset=UTF-8`.

| Endpoint | Method | Status Codes | Description |
|---|---|---|---|
| `/api/members` | `GET` | `200`, `500` | Returns a list of all members `[{"id": 1, "name": "Rick"}, ...]`. |
| `/api/members` | `POST` | `201`, `400`, `409`, `500` | Creates a member from `{"name": "..."}`. Returns created member with generated ID. Rejects duplicates with `409 Conflict`. |
| `/api/expenses` | `GET` | `200`, `500` | Returns all expenses with description, amount, payer ID, and payer name. |
| `/api/expenses` | `POST` | `201`, `400`, `500` | Records an expense from `{"description": "...", "amount": 900, "payerId": 1}`. |
| `/api/summary` | `GET` | `200`, `500` | Returns total expenses, member count, equal fair share, and per-member balances. |
| `/api/settlements`| `GET` | `200`, `500` | Returns simplified settlement transactions `[{"from": "Aman", "to": "Rick", "amount": 100.00}, ...]`. |
| `/` | `GET` | `200`, `404`, `405` | Serves `index.html`. Also serves `/style.css` and `/script.js`. |

---

## 7. Prerequisites

Before running the application, ensure the following software is installed on your computer:

1. **Java Development Kit (JDK) 25**
   - Download: [Oracle Java](https://www.oracle.com/java/technologies/downloads/) or [Eclipse Adoptium Temurin](https://adoptium.net/)
   - Verify installation in terminal:
     ```cmd
     java -version
     javac -version
     ```
2. **Apache Maven (3.8.0 or newer)**
   - Download: [Maven Official Website](https://maven.apache.org/download.cgi)
   - Add the `bin` directory of Maven to your system `PATH`.
   - Verify installation in terminal:
     ```cmd
     mvn -version
     ```
3. **Web Browser**
   - Google Chrome, Microsoft Edge, Mozilla Firefox, or any modern web browser.

---

## 8. Setup and Run Instructions (Windows)

All commands must be executed from the project root directory (`D:\expense-settler`).

### Step 1: Open Terminal in Project Directory
Open **Command Prompt** (`cmd`) or **PowerShell** and navigate to the project root:
```cmd
cd D:\expense-settler
```

### Step 2: Build the Project
Compile the Java source files, copy web resources, and build the JAR package:
```cmd
mvn clean package
```
*Expected output: `BUILD SUCCESS` with the compiled JAR at `target\group-expense-settlement-1.0-SNAPSHOT.jar`.*

### Step 3: Run the Application
Launch the server using Maven:
```cmd
mvn exec:java "-Dexec.mainClass=group.expense.settlement.Main"
```
*When running in standard Windows Command Prompt (`cmd.exe`), quotes can be omitted:*
```cmd
mvn exec:java -Dexec.mainClass=group.expense.settlement.Main
```

### Step 4: Access the Web Interface
Open your browser and navigate to:
```
http://localhost:8080/
```

### Step 5: Stopping the Server
To shut down the server, press `Ctrl + C` in the terminal running the Maven process.

> **Important Note on `expenses.db`**:
> The SQLite database file `expenses.db` is opened and created relative to the working directory from which you launch the program. Running the commands from `D:\expense-settler` ensures `expenses.db` remains located in the project root.

---

## 9. How to Use the Application

1. **Add Group Members**:
   - In the **Add Member** card, type a name (e.g., `Rick`) and click **+ Add Member**.
   - Add at least two or three members so expenses can be shared.
2. **Record Expenses**:
   - In the **Add Expense** card, enter a description (e.g., `Dinner`).
   - Enter a whole-rupee amount (e.g., `900`).
   - Select who paid from the **Paid By** dropdown and click **+ Add Expense**.
3. **Review Summary & Balances**:
   - Check the metric cards at the top for **Total Expenses**, **Group Members**, and **Equal Share / Person**.
   - Look at the **Member Balances & Breakdown** table to see each member's total paid, fair share, net balance, and status badge (*Gets Back*, *Owes*, or *Settled*).
4. **Follow Recommended Settlements**:
   - Scroll to the **Recommended Settlements** section to see direct transfer instructions (e.g., `Aman pays Rick ₹100.00`).
   - If everyone is settled or no expenses exist, a friendly "All Settled Up!" state is displayed.

---

## 10. Worked Example: Settlement Calculation

Consider three friends: **Rick**, **Aman**, and **Riya**.

### Step 1: Expenses Logged
- **Rick** pays **₹900** for *Dinner*.
- **Aman** pays **₹300** for *Cab*.
- **Riya** pays **₹0**.

### Step 2: Summary Calculation
- **Total Expenditure**: ₹900 + ₹300 = **₹1,200.00**
- **Member Count**: 3
- **Equal Share**: ₹1,200 / 3 = **₹400.00** per person

### Step 3: Net Balances
$$\text{Net Balance} = \text{Amount Paid} - \text{Equal Share}$$
- **Rick**: $₹900 - ₹400 = \mathbf{+₹500.00}$ (Creditor — gets back ₹500)
- **Aman**: $₹300 - ₹400 = \mathbf{-₹100.00}$ (Debtor — owes ₹100)
- **Riya**: $₹0 - ₹400 = \mathbf{-₹400.00}$ (Debtor — owes ₹400)

$$\text{Sum of Net Balances} = +500 + (-100) + (-400) = \mathbf{₹0.00}$$

### Step 4: Greedy Settlement Matching
The settlement algorithm pairs debtors with creditors using a two-pointer matching approach:
1. **Aman** pays **Rick** **₹100.00** $\rightarrow$ Aman's debt is cleared; Rick is still owed ₹400.00.
2. **Riya** pays **Rick** **₹400.00** $\rightarrow$ Riya's debt is cleared; Rick is fully settled.

**Total Transfers**: 2 direct transactions settle the group completely without intermediary transfers.

---

## 11. Object-Oriented Programming (OOP) Concepts Applied

The application is structured around foundational OOP principles:

1. **Encapsulation**:
   - Model classes `Member` and `Expense` encapsulate state with `private` fields and expose them via public getters.
   - Internal implementation details of database operations and settlement math are private to `DatabaseManager` and `SettlementManager`.
2. **Single Responsibility Principle (SRP)**:
   - `Member` / `Expense`: Entity data representations.
   - `DatabaseManager`: SQL database connection, schema setup, and query execution.
   - `SettlementManager`: Financial domain rules, balance computations, and settlement logic.
   - `WebServer`: HTTP routing, status code dispatching, request/response headers, and JSON serialization.
   - `Main`: Application lifecycle and startup coordination.
3. **Abstraction**:
   - Callers interact with simple methods such as `SettlementManager.getSummary()` and `SettlementManager.getSettlements()` without needing to know whether data is stored in SQLite, memory, or another storage engine.
4. **Separation of Concerns**:
   - The presentation layer (HTML/CSS/JS) is fully decoupled from the business logic layer (Java) through a well-defined RESTful JSON interface.

---

## 12. Recommended `.gitignore`

If tracking this project with Git, create a `.gitignore` file in the project root with the following entries:

```gitignore
# Maven build output
target/

# Local SQLite database
expenses.db

# Temporary classpath dumps
classpath.txt

# IDE configuration files
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Operating system files
Thumbs.db
.DS_Store
```

---

## 13. Current Limitations

- **Fixed Group**: Supports a single shared group per database instance.
- **Member & Expense Caps**: Enforces a maximum of 10 members (`MAX_MEMBERS = 10`) and 50 expenses (`MAX_EXPENSES = 50`).
- **Equal Splits Only**: Expenses are divided equally among all existing members; unequal percentages or custom shares are not supported.
- **Integer Rupee Inputs**: Expense amounts are entered as positive whole numbers (rupees), though calculations and settlements support two decimal places (paise).
- **Single Host / Development Server**: Runs on `localhost:8080` using Java's built-in `HttpServer` with the default executor (`server.setExecutor(null)`), where requests are dispatched synchronously on the server's internal dispatcher thread rather than a multi-threaded pool, intended for personal and local group use rather than high-concurrency production deployment.
