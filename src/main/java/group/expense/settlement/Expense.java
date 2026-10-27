package group.expense.settlement;

public class Expense {
    private int id;
    private String description;
    private int amount;
    private int payerId;
    private String payerName;

    public Expense(int id, String description, int amount, int payerId, String payerName) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.payerId = payerId;
        this.payerName = payerName;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public int getAmount() {
        return amount;
    }

    public int getPayerId() {
        return payerId;
    }

    public String getPayerName() {
        return payerName;
    }
}
