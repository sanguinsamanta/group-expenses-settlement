package group.expense.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettlementManager {

    private static final int MAX_MEMBERS = 10;
    private static final int MAX_EXPENSES = 50;

    /**
     * Adds a new member after validation:
     * - Trims leading and trailing whitespace.
     * - Rejects null or blank names.
     * - Enforces a maximum limit of 10 members.
     * - Delegates saving to DatabaseManager.
     */
    public static int addMember(String name) throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Member name cannot be blank.");
        }

        String trimmedName = name.trim();

        List<Member> existingMembers = DatabaseManager.getAllMembers();
        if (existingMembers.size() >= MAX_MEMBERS) {
            throw new IllegalArgumentException("Member limit reached. Maximum allowed members is " + MAX_MEMBERS + ".");
        }

        return DatabaseManager.addMember(trimmedName);
    }

    /**
     * Retrieves all members by delegating to DatabaseManager.
     */
    public static List<Member> getAllMembers() throws SQLException {
        return DatabaseManager.getAllMembers();
    }

    /**
     * Adds a new expense after validation:
     * - Trims and validates description (cannot be null or blank).
     * - Requires amount to be positive whole-rupee (> 0).
     * - Enforces a maximum limit of 50 expenses.
     * - Verifies that the payer ID exists in members.
     * - Delegates saving to DatabaseManager.
     */
    public static int addExpense(String description, int amount, int payerId) throws SQLException {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Expense description cannot be blank.");
        }

        String trimmedDescription = description.trim();

        if (amount <= 0) {
            throw new IllegalArgumentException("Expense amount must be a positive whole number greater than 0.");
        }

        List<Expense> existingExpenses = DatabaseManager.getAllExpenses();
        if (existingExpenses.size() >= MAX_EXPENSES) {
            throw new IllegalArgumentException("Expense limit reached. Maximum allowed expenses is " + MAX_EXPENSES + ".");
        }

        List<Member> members = DatabaseManager.getAllMembers();
        boolean payerExists = members.stream().anyMatch(m -> m.getId() == payerId);
        if (!payerExists) {
            throw new IllegalArgumentException("Selected payer does not exist.");
        }

        return DatabaseManager.addExpense(trimmedDescription, amount, payerId);
    }

    /**
     * Retrieves all expenses by delegating to DatabaseManager.
     */
    public static List<Expense> getAllExpenses() throws SQLException {
        return DatabaseManager.getAllExpenses();
    }

    /**
     * Computes the financial summary across all group members:
     * - Total expenses incurred.
     * - Equal fair share per member (2 decimal places).
     * - For each member: total amount paid and net balance (paid - fair share).
     */
    public static Map<String, Object> getSummary() throws SQLException {
        List<Member> members = DatabaseManager.getAllMembers();
        List<Expense> expenses = DatabaseManager.getAllExpenses();

        int memberCount = members.size();

        // Calculate total expenses sum in Rupees
        long totalExpenseRupees = 0;
        // Map memberId -> total amount paid in Rupees
        Map<Integer, Long> paidMap = new HashMap<>();
        for (Member m : members) {
            paidMap.put(m.getId(), 0L);
        }

        for (Expense e : expenses) {
            totalExpenseRupees += e.getAmount();
            paidMap.put(e.getPayerId(), paidMap.getOrDefault(e.getPayerId(), 0L) + e.getAmount());
        }

        // Calculate equal share using BigDecimal for exact 2-decimal rounding
        BigDecimal equalShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (memberCount > 0) {
            equalShare = BigDecimal.valueOf(totalExpenseRupees)
                    .divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);
        }

        // Build list of member details with paid amount and balance
        List<Map<String, Object>> memberDetails = new ArrayList<>();
        for (Member m : members) {
            long paid = paidMap.getOrDefault(m.getId(), 0L);
            BigDecimal paidBd = BigDecimal.valueOf(paid).setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = paidBd.subtract(equalShare);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", m.getId());
            detail.put("name", m.getName());
            detail.put("paid", paidBd);
            detail.put("balance", balance);
            memberDetails.add(detail);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalExpenses", BigDecimal.valueOf(totalExpenseRupees).setScale(2, RoundingMode.HALF_UP));
        summary.put("memberCount", memberCount);
        summary.put("equalShare", equalShare);
        summary.put("members", memberDetails);

        return summary;
    }

    /**
     * Helper record to track a debtor or creditor during settlement matching.
     */
    private static class BalancePerson {
        String name;
        long amountPaise; // positive amount

        BalancePerson(String name, long amountPaise) {
            this.name = name;
            this.amountPaise = amountPaise;
        }
    }

    /**
     * Calculates simplified settlement transfers between members.
     * Uses integer paise (1 Rupee = 100 Paise) throughout to avoid floating-point errors.
     * 
     * To ensure that total debts and credits always sum to exactly zero:
     * - We divide totalExpensesPaise by memberCount: baseShare = total / count.
     * - The remainder (total % count) paise are distributed 1 paise each to the first 'remainder' members.
     * - This guarantees sum(share) == totalExpensesPaise, so sum(paid - share) == 0 exactly.
     * Matches debtors (those who owe money) with creditors (those who should receive money).
     */
    public static List<Map<String, Object>> getSettlements() throws SQLException {
        List<Member> members = DatabaseManager.getAllMembers();
        List<Expense> expenses = DatabaseManager.getAllExpenses();

        // No transfers needed or possible if fewer than 2 members
        if (members.size() < 2) {
            return new ArrayList<>();
        }

        int memberCount = members.size();

        // Calculate total expenses in paise
        long totalExpensesPaise = 0;
        Map<Integer, Long> paidMapPaise = new HashMap<>();
        for (Member m : members) {
            paidMapPaise.put(m.getId(), 0L);
        }

        for (Expense e : expenses) {
            long expensePaise = ((long) e.getAmount()) * 100L;
            totalExpensesPaise += expensePaise;
            paidMapPaise.put(e.getPayerId(), paidMapPaise.getOrDefault(e.getPayerId(), 0L) + expensePaise);
        }

        if (totalExpensesPaise == 0) {
            return new ArrayList<>();
        }

        // 1. Calculate baseShare = totalExpensesPaise / memberCount
        long baseSharePaise = totalExpensesPaise / memberCount;

        // 2. Calculate remainder = totalExpensesPaise % memberCount
        long remainderPaise = totalExpensesPaise % memberCount;

        List<BalancePerson> debtors = new ArrayList<>();   // Members who owe money (negative balance)
        List<BalancePerson> creditors = new ArrayList<>(); // Members who are owed money (positive balance)

        // 3 & 4. In ID order (DatabaseManager.getAllMembers() orders by ID ASC):
        // Assign fair share: baseShare + 1 to the first 'remainder' members, and baseShare to the rest.
        // Calculate each net balance as paidPaise - assignedSharePaise.
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            long paidPaise = paidMapPaise.getOrDefault(m.getId(), 0L);

            long assignedSharePaise = baseSharePaise + (i < remainderPaise ? 1 : 0);
            long netPaise = paidPaise - assignedSharePaise;

            if (netPaise < 0) {
                // Owes money
                debtors.add(new BalancePerson(m.getName(), -netPaise));
            } else if (netPaise > 0) {
                // Is owed money
                creditors.add(new BalancePerson(m.getName(), netPaise));
            }
        }

        List<Map<String, Object>> settlements = new ArrayList<>();

        // Greedy two-pointer matching:
        // Settle as much as possible between the current debtor and current creditor.
        int dIdx = 0;
        int cIdx = 0;

        while (dIdx < debtors.size() && cIdx < creditors.size()) {
            BalancePerson debtor = debtors.get(dIdx);
            BalancePerson creditor = creditors.get(cIdx);

            long transferPaise = Math.min(debtor.amountPaise, creditor.amountPaise);

            if (transferPaise > 0) {
                BigDecimal transferAmount = BigDecimal.valueOf(transferPaise)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                Map<String, Object> transfer = new LinkedHashMap<>();
                transfer.put("from", debtor.name);
                transfer.put("to", creditor.name);
                transfer.put("amount", transferAmount);
                settlements.add(transfer);

                debtor.amountPaise -= transferPaise;
                creditor.amountPaise -= transferPaise;
            }

            if (debtor.amountPaise == 0) {
                dIdx++;
            }
            if (creditor.amountPaise == 0) {
                cIdx++;
            }
        }

        return settlements;
    }
}
