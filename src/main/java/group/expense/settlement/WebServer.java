package group.expense.settlement;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public/";

    // Mapping of allowed filenames to their respective MIME types
    private static final Map<String, String> MIME_TYPES = Map.of(
            "index.html", "text/html; charset=UTF-8",
            "style.css", "text/css; charset=UTF-8",
            "script.js", "application/javascript; charset=UTF-8"
    );

    /**
     * Reusable helper to send an HTTP response with a given status code, content type, and body.
     */
    public static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] responseBody) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        }
    }

    /**
     * Helper to send plain text responses with UTF-8 encoding.
     */
    public static void sendTextResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, statusCode, "text/plain; charset=UTF-8", bytes);
    }

    /**
     * Helper to send JSON responses with UTF-8 encoding.
     */
    public static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonString) throws IOException {
        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, statusCode, "application/json; charset=UTF-8", bytes);
    }

    /**
     * Escapes standard special characters for valid JSON strings.
     */
    public static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a single-field JSON object: {"name": "..."} strictly and completely.
     * Rejects any extra keys, malformed JSON, and invalid escape sequences.
     */
    public static String parseSingleNameJsonObject(String json) throws IllegalArgumentException {
        if (json == null) {
            throw new IllegalArgumentException("Request body cannot be null.");
        }

        int len = json.length();
        int idx = 0;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != '{') {
            throw new IllegalArgumentException("JSON must start with '{'.");
        }
        idx++;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != '"') {
            throw new IllegalArgumentException("Expected string key in JSON object.");
        }

        StringAndIndex keyResult = parseJsonStringToken(json, idx);
        if (!"name".equals(keyResult.value)) {
            throw new IllegalArgumentException("Expected 'name' property as key.");
        }
        idx = keyResult.nextIndex;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != ':') {
            throw new IllegalArgumentException("Expected ':' after 'name' key.");
        }
        idx++;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != '"') {
            throw new IllegalArgumentException("Value of 'name' must be a string.");
        }

        StringAndIndex valueResult = parseJsonStringToken(json, idx);
        String nameValue = valueResult.value;
        idx = valueResult.nextIndex;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != '}') {
            throw new IllegalArgumentException("Expected closing '}' without additional properties.");
        }
        idx++;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx != len) {
            throw new IllegalArgumentException("Unexpected content after closing brace.");
        }

        return nameValue;
    }

    /**
     * Data holder for parsed expense input.
     */
    public static class ExpenseInput {
        public final String description;
        public final int amount;
        public final int payerId;

        public ExpenseInput(String description, int amount, int payerId) {
            this.description = description;
            this.amount = amount;
            this.payerId = payerId;
        }
    }

    /**
     * Parses and strictly validates JSON object for expense creation:
     * {"description": "...", "amount": 123, "payerId": 1}
     * Expects exactly these 3 fields with no extra or missing properties.
     */
    public static ExpenseInput parseExpenseJsonObject(String json) throws IllegalArgumentException {
        if (json == null) {
            throw new IllegalArgumentException("Request body cannot be null.");
        }

        int len = json.length();
        int idx = 0;

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx >= len || json.charAt(idx) != '{') {
            throw new IllegalArgumentException("JSON must start with '{'.");
        }
        idx++;

        Map<String, Object> fields = new HashMap<>();

        while (true) {
            while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

            if (idx >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON object.");
            }

            if (json.charAt(idx) == '}') {
                idx++;
                break;
            }

            if (json.charAt(idx) != '"') {
                throw new IllegalArgumentException("Expected string property key.");
            }

            StringAndIndex keyResult = parseJsonStringToken(json, idx);
            String key = keyResult.value;
            idx = keyResult.nextIndex;

            if (fields.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate key in JSON: " + key);
            }

            while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

            if (idx >= len || json.charAt(idx) != ':') {
                throw new IllegalArgumentException("Expected ':' after property key: " + key);
            }
            idx++;

            while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

            if (idx >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON after ':' for property " + key);
            }

            char valChar = json.charAt(idx);
            if ("description".equals(key)) {
                if (valChar != '"') {
                    throw new IllegalArgumentException("Property 'description' must be a string.");
                }
                StringAndIndex valResult = parseJsonStringToken(json, idx);
                fields.put("description", valResult.value);
                idx = valResult.nextIndex;
            } else if ("amount".equals(key) || "payerId".equals(key)) {
                if (valChar == '"') {
                    throw new IllegalArgumentException("Property '" + key + "' must be an integer, not a string.");
                }
                int numStart = idx;
                if (json.charAt(idx) == '-') {
                    idx++;
                }
                while (idx < len && Character.isDigit(json.charAt(idx))) {
                    idx++;
                }
                if (numStart == idx || (numStart + 1 == idx && json.charAt(numStart) == '-')) {
                    throw new IllegalArgumentException("Invalid integer value for property '" + key + "'.");
                }
                String numStr = json.substring(numStart, idx);
                try {
                    int numVal = Integer.parseInt(numStr);
                    fields.put(key, numVal);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Integer value out of range for property '" + key + "'.");
                }
            } else {
                throw new IllegalArgumentException("Unknown property in expense JSON: " + key);
            }

            while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

            if (idx >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON.");
            }

            if (json.charAt(idx) == ',') {
                idx++;
            } else if (json.charAt(idx) == '}') {
                idx++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' after property value.");
            }
        }

        while (idx < len && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx != len) {
            throw new IllegalArgumentException("Unexpected content after closing brace.");
        }

        if (!fields.containsKey("description") || !fields.containsKey("amount") || !fields.containsKey("payerId")) {
            throw new IllegalArgumentException("Missing required expense fields: 'description', 'amount', 'payerId'.");
        }

        String description = (String) fields.get("description");
        int amount = (Integer) fields.get("amount");
        int payerId = (Integer) fields.get("payerId");

        if (description.trim().isEmpty()) {
            throw new IllegalArgumentException("Expense description cannot be blank.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Expense amount must be a positive whole number greater than 0.");
        }
        if (payerId <= 0) {
            throw new IllegalArgumentException("Payer ID must be a positive integer.");
        }

        return new ExpenseInput(description, amount, payerId);
    }

    private static class StringAndIndex {
        final String value;
        final int nextIndex;

        StringAndIndex(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    /**
     * Parses a quoted JSON string starting at quoteIndex, handling escapes and Unicode escapes.
     */
    private static StringAndIndex parseJsonStringToken(String json, int quoteIndex) throws IllegalArgumentException {
        StringBuilder sb = new StringBuilder();
        int i = quoteIndex + 1;
        int len = json.length();

        while (i < len) {
            char c = json.charAt(i);
            if (c == '"') {
                return new StringAndIndex(sb.toString(), i + 1);
            }
            if (c == '\\') {
                i++;
                if (i >= len) {
                    throw new IllegalArgumentException("Incomplete escape sequence at end of string.");
                }
                char esc = json.charAt(i);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 >= len) {
                            throw new IllegalArgumentException("Incomplete unicode escape sequence.");
                        }
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            int codePoint = Integer.parseInt(hex, 16);
                            sb.append((char) codePoint);
                            i += 4;
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid unicode hex escape: \\u" + hex);
                        }
                    }
                    default -> throw new IllegalArgumentException("Invalid escape sequence: \\" + esc);
                }
            } else {
                if (c < 0x20) {
                    throw new IllegalArgumentException("Unescaped control character in JSON string.");
                }
                sb.append(c);
            }
            i++;
        }

        throw new IllegalArgumentException("Unterminated string literal.");
    }

    /**
     * Handler for /api/members endpoint supporting GET and POST.
     */
    private static class MembersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Strict route check: only handle exact /api/members
            if (!"/api/members".equals(path)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                handleGetMembers(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePostMember(exchange);
            } else {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }

        private void handleGetMembers(HttpExchange exchange) throws IOException {
            try {
                List<Member> members = SettlementManager.getAllMembers();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < members.size(); i++) {
                    Member m = members.get(i);
                    json.append("{\"id\":").append(m.getId())
                        .append(",\"name\":\"").append(escapeJson(m.getName())).append("\"}");
                    if (i < members.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void handlePostMember(HttpExchange exchange) throws IOException {
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            String name;
            try {
                name = parseSingleNameJsonObject(body);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            try {
                int id = SettlementManager.addMember(name);
                String responseJson = "{\"id\":" + id + ",\"name\":\"" + escapeJson(name.trim()) + "\"}";
                sendJsonResponse(exchange, 201, responseJson);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (SQLException e) {
                // Return 409 ONLY for members.name unique constraint violation
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("unique") && msg.contains("members.name")) {
                    sendJsonResponse(exchange, 409, "{\"error\":\"Member name already exists.\"}");
                } else {
                    sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
                }
            }
        }
    }

    /**
     * Handler for /api/expenses endpoint supporting GET and POST.
     */
    private static class ExpensesApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Strict route check: only handle exact /api/expenses
            if (!"/api/expenses".equals(path)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                handleGetExpenses(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePostExpense(exchange);
            } else {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }

        private void handleGetExpenses(HttpExchange exchange) throws IOException {
            try {
                List<Expense> expenses = SettlementManager.getAllExpenses();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < expenses.size(); i++) {
                    Expense e = expenses.get(i);
                    json.append("{\"id\":").append(e.getId())
                        .append(",\"description\":\"").append(escapeJson(e.getDescription())).append("\"")
                        .append(",\"amount\":").append(e.getAmount())
                        .append(",\"payerId\":").append(e.getPayerId())
                        .append(",\"payerName\":\"").append(escapeJson(e.getPayerName())).append("\"}");
                    if (i < expenses.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void handlePostExpense(HttpExchange exchange) throws IOException {
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            ExpenseInput input;
            try {
                input = parseExpenseJsonObject(body);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            try {
                int id = SettlementManager.addExpense(input.description, input.amount, input.payerId);
                String responseJson = "{\"id\":" + id
                        + ",\"description\":\"" + escapeJson(input.description.trim()) + "\""
                        + ",\"amount\":" + input.amount
                        + ",\"payerId\":" + input.payerId + "}";
                sendJsonResponse(exchange, 201, responseJson);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler for /api/summary endpoint supporting GET only.
     */
    private static class SummaryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (!"/api/summary".equals(path)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, Object> summary = SettlementManager.getSummary();

                StringBuilder json = new StringBuilder("{");
                json.append("\"totalExpenses\":").append(summary.get("totalExpenses")).append(",");
                json.append("\"memberCount\":").append(summary.get("memberCount")).append(",");
                json.append("\"equalShare\":").append(summary.get("equalShare")).append(",");
                json.append("\"members\":[");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> members = (List<Map<String, Object>>) summary.get("members");
                for (int i = 0; i < members.size(); i++) {
                    Map<String, Object> m = members.get(i);
                    json.append("{")
                        .append("\"id\":").append(m.get("id")).append(",")
                        .append("\"name\":\"").append(escapeJson((String) m.get("name"))).append("\",")
                        .append("\"paid\":").append(m.get("paid")).append(",")
                        .append("\"balance\":").append(m.get("balance"))
                        .append("}");
                    if (i < members.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]}");

                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Server error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler for /api/settlements endpoint supporting GET only.
     */
    private static class SettlementsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (!"/api/settlements".equals(path)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                List<Map<String, Object>> settlements = SettlementManager.getSettlements();

                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < settlements.size(); i++) {
                    Map<String, Object> s = settlements.get(i);
                    json.append("{")
                        .append("\"from\":\"").append(escapeJson((String) s.get("from"))).append("\",")
                        .append("\"to\":\"").append(escapeJson((String) s.get("to"))).append("\",")
                        .append("\"amount\":").append(s.get("amount"))
                        .append("}");
                    if (i < settlements.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");

                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Server error: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler to serve static frontend files (index.html, style.css, script.js).
     * Prevents path traversal and checks for allowed files.
     */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Only allow GET requests for static files
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendTextResponse(exchange, 405, "405 Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();

            // Root path maps to index.html
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            // Path traversal protection: strip leading slash and verify no directory traversal tokens
            String filename = path.startsWith("/") ? path.substring(1) : path;
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                sendTextResponse(exchange, 404, "404 Not Found");
                return;
            }

            // Only serve known allowed public files
            String contentType = MIME_TYPES.get(filename);
            if (contentType == null) {
                sendTextResponse(exchange, 404, "404 Not Found");
                return;
            }

            // Load file resource from classpath (src/main/resources/public/)
            String resourcePath = PUBLIC_DIR + filename;
            try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendTextResponse(exchange, 404, "404 Not Found: " + filename);
                    return;
                }

                byte[] fileBytes = is.readAllBytes();
                sendResponse(exchange, 200, contentType, fileBytes);
            }
        }
    }

    /**
     * Creates and starts the HTTP server on localhost:8080.
     */
    public static HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);

        // API contexts: routes API handlers before falling back to static files
        server.createContext("/api/members", new MembersApiHandler());
        server.createContext("/api/expenses", new ExpensesApiHandler());
        server.createContext("/api/summary", new SummaryApiHandler());
        server.createContext("/api/settlements", new SettlementsApiHandler());

        // Static files handler handles root and general requests
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("SortmyExpense server running at http://" + HOST + ":" + PORT + "/");
        return server;
    }

    public static void main(String[] args) {
        try {
            // Initialize database tables before launching web server
            DatabaseManager.initializeDatabase();
            startServer();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
