/**
 * SortmyExpense - Group Expense Settlement Frontend Script
 * Beginner-friendly vanilla JavaScript managing API interactions and UI updates.
 */

// Global cached state
let currentMembers = [];

// DOM Element References
const alertBanner = document.getElementById('alert-banner');
const alertMessage = document.getElementById('alert-message');
const alertCloseBtn = document.getElementById('alert-close-btn');

// Metrics elements
const statTotalExpenses = document.getElementById('stat-total-expenses');
const statMemberCount = document.getElementById('stat-member-count');
const statEqualShare = document.getElementById('stat-equal-share');

// Forms & Inputs
const memberForm = document.getElementById('member-form');
const memberNameInput = document.getElementById('member-name-input');
const expenseForm = document.getElementById('expense-form');
const expenseDescInput = document.getElementById('expense-desc-input');
const expenseAmountInput = document.getElementById('expense-amount-input');
const expensePayerSelect = document.getElementById('expense-payer-select');

// Tables & Lists
const membersTbody = document.getElementById('members-tbody');
const expensesTbody = document.getElementById('expenses-tbody');
const balancesTbody = document.getElementById('balances-tbody');
const membersCountTag = document.getElementById('members-count-tag');
const expensesCountTag = document.getElementById('expenses-count-tag');
const settlementList = document.getElementById('settlement-list');
const settlementsEmptyState = document.getElementById('settlements-empty-state');

/**
 * Format a number or numeric string to standard Indian Rupee format with 2 decimals.
 */
function formatCurrency(val) {
    const num = Number(val) || 0;
    return '₹' + num.toFixed(2);
}

/**
 * Escape HTML to prevent basic injection when rendering strings into DOM.
 */
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/**
 * Display a user-friendly alert message (success or error).
 */
function showAlert(message, isError = false) {
    alertMessage.textContent = message;
    alertBanner.className = `alert-banner ${isError ? 'error' : 'success'}`;
    alertBanner.classList.remove('hidden');

    // Automatically hide after 6 seconds
    setTimeout(() => {
        alertBanner.classList.add('hidden');
    }, 6000);
}

// Close alert button handler
alertCloseBtn.addEventListener('click', () => {
    alertBanner.classList.add('hidden');
});

/**
 * Fetch all members from GET /api/members and update dropdown + table.
 */
async function loadMembers() {
    try {
        const res = await fetch('/api/members');
        if (!res.ok) {
            throw new Error(`Failed to load members (${res.status})`);
        }
        currentMembers = await res.json();
        renderMembersTable(currentMembers);
        updatePayerDropdown(currentMembers);
    } catch (err) {
        console.error('Error loading members:', err);
        showAlert('Could not load members: ' + err.message, true);
    }
}

/**
 * Render members into the Members Table.
 */
function renderMembersTable(members) {
    membersCountTag.textContent = `${members.length} member${members.length === 1 ? '' : 's'}`;

    if (!members || members.length === 0) {
        membersTbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="2">No members added yet. Add one above!</td>
            </tr>`;
        return;
    }

    let html = '';
    members.forEach(m => {
        html += `
            <tr>
                <td>#${m.id}</td>
                <td><strong>${escapeHtml(m.name)}</strong></td>
            </tr>`;
    });
    membersTbody.innerHTML = html;
}

/**
 * Populate the Payer select dropdown with current members.
 */
function updatePayerDropdown(members) {
    const currentVal = expensePayerSelect.value;
    let html = '<option value="" disabled selected>Select member</option>';

    members.forEach(m => {
        html += `<option value="${m.id}">${escapeHtml(m.name)} (ID: ${m.id})</option>`;
    });

    expensePayerSelect.innerHTML = html;

    // Restore selected value if member still exists
    if (currentVal && members.some(m => String(m.id) === String(currentVal))) {
        expensePayerSelect.value = currentVal;
    }
}

/**
 * Fetch all recorded expenses from GET /api/expenses.
 */
async function loadExpenses() {
    try {
        const res = await fetch('/api/expenses');
        if (!res.ok) {
            throw new Error(`Failed to load expenses (${res.status})`);
        }
        const expenses = await res.json();
        renderExpensesTable(expenses);
    } catch (err) {
        console.error('Error loading expenses:', err);
        showAlert('Could not load expenses: ' + err.message, true);
    }
}

/**
 * Render expenses table.
 */
function renderExpensesTable(expenses) {
    expensesCountTag.textContent = `${expenses.length} expense${expenses.length === 1 ? '' : 's'}`;

    if (!expenses || expenses.length === 0) {
        expensesTbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="3">No expenses logged yet. Record one above!</td>
            </tr>`;
        return;
    }

    let html = '';
    expenses.forEach(e => {
        html += `
            <tr>
                <td>${escapeHtml(e.description)}</td>
                <td><strong>${formatCurrency(e.amount)}</strong></td>
                <td>${escapeHtml(e.payerName)}</td>
            </tr>`;
    });
    expensesTbody.innerHTML = html;
}

/**
 * Fetch financial summary from GET /api/summary.
 */
async function loadSummary() {
    try {
        const res = await fetch('/api/summary');
        if (!res.ok) {
            throw new Error(`Failed to load summary (${res.status})`);
        }
        const summary = await res.json();
        renderSummary(summary);
    } catch (err) {
        console.error('Error loading summary:', err);
        showAlert('Could not load summary: ' + err.message, true);
    }
}

/**
 * Update KPI cards and Member Balances table from summary data.
 */
function renderSummary(summary) {
    // 1. KPI cards
    statTotalExpenses.textContent = formatCurrency(summary.totalExpenses);
    statMemberCount.textContent = summary.memberCount;
    statEqualShare.textContent = formatCurrency(summary.equalShare);

    // 2. Member balances breakdown table
    const members = summary.members || [];
    if (members.length === 0) {
        balancesTbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="5">Add members and expenses to view balances.</td>
            </tr>`;
        return;
    }

    let html = '';
    members.forEach(m => {
        const balance = Number(m.balance) || 0;
        let balanceClass = 'balance-zero';
        let statusBadge = '<span class="status-pill settled">Settled</span>';

        if (balance > 0.005) {
            balanceClass = 'balance-positive';
            statusBadge = '<span class="status-pill gets-back">Gets Back</span>';
        } else if (balance < -0.005) {
            balanceClass = 'balance-negative';
            statusBadge = '<span class="status-pill owes">Owes</span>';
        }

        html += `
            <tr>
                <td><strong>${escapeHtml(m.name)}</strong></td>
                <td>${formatCurrency(m.paid)}</td>
                <td>${formatCurrency(summary.equalShare)}</td>
                <td class="${balanceClass}">${balance > 0 ? '+' : ''}${formatCurrency(balance)}</td>
                <td>${statusBadge}</td>
            </tr>`;
    });
    balancesTbody.innerHTML = html;
}

/**
 * Fetch simplified settlement suggestions from GET /api/settlements.
 */
async function loadSettlements() {
    try {
        const res = await fetch('/api/settlements');
        if (!res.ok) {
            throw new Error(`Failed to load settlements (${res.status})`);
        }
        const settlements = await res.json();
        renderSettlements(settlements);
    } catch (err) {
        console.error('Error loading settlements:', err);
        showAlert('Could not load settlements: ' + err.message, true);
    }
}

/**
 * Render settlements or friendly empty state.
 */
function renderSettlements(settlements) {
    if (!settlements || settlements.length === 0) {
        settlementList.classList.add('hidden');
        settlementsEmptyState.classList.remove('hidden');
        return;
    }

    settlementsEmptyState.classList.add('hidden');
    settlementList.classList.remove('hidden');

    let html = '';
    settlements.forEach(s => {
        html += `
            <li class="settlement-item">
                <div class="transfer-statement">
                    <span class="person-name">${escapeHtml(s.from)}</span>
                    <span class="transfer-verb">pays</span>
                    <span class="person-name">${escapeHtml(s.to)}</span>
                </div>
                <div class="transfer-amount">${formatCurrency(s.amount)}</div>
            </li>`;
    });
    settlementList.innerHTML = html;
}

/**
 * Refresh all sections of the application.
 */
async function refreshAll() {
    await Promise.all([
        loadMembers(),
        loadExpenses(),
        loadSummary(),
        loadSettlements()
    ]);
}

/**
 * Handle Add Member form submission.
 */
memberForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = memberNameInput.value.trim();

    // Frontend validation
    if (!name) {
        showAlert('Please enter a valid member name.', true);
        memberNameInput.focus();
        return;
    }

    try {
        const response = await fetch('/api/members', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to add member');
        }

        showAlert(`Member "${data.name}" added successfully!`);
        memberNameInput.value = '';
        await refreshAll();
    } catch (err) {
        showAlert(err.message, true);
    }
});

/**
 * Handle Add Expense form submission.
 */
expenseForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const description = expenseDescInput.value.trim();
    const amountStr = expenseAmountInput.value.trim();
    const payerIdStr = expensePayerSelect.value;

    // Frontend validations
    if (!description) {
        showAlert('Please enter an expense description.', true);
        expenseDescInput.focus();
        return;
    }

    const amount = parseInt(amountStr, 10);
    if (isNaN(amount) || amount <= 0 || !Number.isInteger(Number(amountStr))) {
        showAlert('Expense amount must be a positive whole number greater than 0.', true);
        expenseAmountInput.focus();
        return;
    }

    const payerId = parseInt(payerIdStr, 10);
    if (!payerIdStr || isNaN(payerId) || payerId <= 0) {
        showAlert('Please select who paid for this expense.', true);
        expensePayerSelect.focus();
        return;
    }

    try {
        const response = await fetch('/api/expenses', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                description,
                amount,
                payerId
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'Failed to add expense');
        }

        showAlert(`Expense "${data.description}" of ₹${data.amount} recorded!`);
        expenseDescInput.value = '';
        expenseAmountInput.value = '';
        expensePayerSelect.value = '';
        await refreshAll();
    } catch (err) {
        showAlert(err.message, true);
    }
});

// Initial boot
document.addEventListener('DOMContentLoaded', () => {
    refreshAll();
});
