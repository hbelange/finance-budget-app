package com.hbelange.financebudgetapp.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.dto.AllocationRequest;
import com.hbelange.financebudgetapp.dto.BudgetCategoryDTO;
import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.dto.GoalRequest;
import com.hbelange.financebudgetapp.dto.TransactionRequest;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.enums.RolloverType;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
import com.hbelange.financebudgetapp.repository.GoalRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;
import com.hbelange.financebudgetapp.service.AccountService;
import com.hbelange.financebudgetapp.service.BudgetService;
import com.hbelange.financebudgetapp.service.CategoryService;
import com.hbelange.financebudgetapp.service.GoalService;
import com.hbelange.financebudgetapp.service.TransactionService;

/**
 * Deletes and reseeds a small, realistic budget for the demo account so the public demo always
 * shows a fresh, working YNAB-style setup. Dates are computed relative to {@link LocalDate#now()}
 * at call time so the data never goes stale, even though {@code reset()} only actually runs when
 * {@code app.demo.enabled} is true (see {@link DemoResetScheduler}).
 */
@Service
public class DemoDataService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final GoalRepository goalRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final CategoryGroupRepository categoryGroupRepository;

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final GoalService goalService;

    private final String demoUserSub;

    public DemoDataService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            BudgetAllocationRepository budgetAllocationRepository,
            GoalRepository goalRepository,
            BudgetCategoryRepository budgetCategoryRepository,
            CategoryGroupRepository categoryGroupRepository,
            AccountService accountService,
            CategoryService categoryService,
            TransactionService transactionService,
            BudgetService budgetService,
            GoalService goalService,
            @Value("${app.demo.user-sub}") String demoUserSub) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.goalRepository = goalRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.categoryGroupRepository = categoryGroupRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.demoUserSub = demoUserSub;
    }

    /** Wipes all data owned by the demo user and reseeds a fresh, realistic budget. */
    @Transactional
    public void reset() {
        deleteExistingDemoData();

        Map<String, UUID> accountIdsByName = createAccounts();
        Map<String, UUID> categoryIdsByName = createCategories();
        createTransactions(accountIdsByName, categoryIdsByName);
        createAllocations(categoryIdsByName);
        createGoals(categoryIdsByName);
    }

    // --- deletion, FK-safe order: transactions -> allocations -> goals -> categories -> groups -> accounts ---

    private void deleteExistingDemoData() {
        transactionRepository.deleteByAccount_UserSub(demoUserSub);
        budgetAllocationRepository.deleteByUserSub(demoUserSub);
        goalRepository.deleteByCategoryGroupUserSub(demoUserSub);
        budgetCategoryRepository.deleteByGroupUserSub(demoUserSub);
        categoryGroupRepository.deleteByUserSub(demoUserSub);
        accountRepository.deleteByUserSub(demoUserSub);
    }

    // --- accounts ---

    private Map<String, UUID> createAccounts() {
        Map<String, UUID> ids = new LinkedHashMap<>();
        AccountDTO checking = accountService.create(new AccountRequest("Checking", AccountType.CHECKING), demoUserSub);
        AccountDTO savings = accountService.create(new AccountRequest("Savings", AccountType.SAVINGS), demoUserSub);
        ids.put(checking.name(), checking.id());
        ids.put(savings.name(), savings.id());
        return ids;
    }

    // --- category groups + categories, matching the default seed used for real users (V6) ---

    private Map<String, UUID> createCategories() {
        Map<String, UUID> ids = new LinkedHashMap<>();
        createGroup("Housing", List.of("Rent / Mortgage", "Utilities", "Internet", "Home Insurance"), ids);
        createGroup("Food", List.of("Groceries", "Dining Out"), ids);
        createGroup("Transport", List.of("Gas", "Car Insurance", "Public Transit"), ids);
        createGroup("Savings", List.of("Emergency Fund", "Retirement", "Goals"), ids);
        return ids;
    }

    private void createGroup(String groupName, List<String> categoryNames, Map<String, UUID> ids) {
        CategoryGroupDTO group = categoryService.createGroup(new CategoryGroupRequest(groupName), demoUserSub);
        for (String categoryName : categoryNames) {
            CategoryGroupDTO updated =
                categoryService.addCategory(group.id(), new BudgetCategoryRequest(categoryName), demoUserSub);
            UUID categoryId = updated.categories().stream()
                .filter(c -> c.name().equals(categoryName))
                .map(BudgetCategoryDTO::id)
                .reduce((first, last) -> last)
                .orElseThrow();
            ids.put(categoryName, categoryId);
        }
    }

    // --- transactions: ~2 months of varied, realistic activity, dated relative to today ---

    private record DemoTransaction(int daysAgo, String accountName, String payee, String categoryName,
                                    BigDecimal amount, boolean cleared) {
    }

    private List<DemoTransaction> transactionPlan() {
        return List.of(
            // Income (Checking) - roughly biweekly paychecks, uncategorized
            new DemoTransaction(58, "Checking", "Employer Payroll", null, new BigDecimal("1500.00"), true),
            new DemoTransaction(44, "Checking", "Employer Payroll", null, new BigDecimal("1500.00"), true),
            new DemoTransaction(30, "Checking", "Employer Payroll", null, new BigDecimal("1500.00"), true),
            new DemoTransaction(16, "Checking", "Employer Payroll", null, new BigDecimal("1500.00"), true),
            new DemoTransaction(2, "Checking", "Employer Payroll", null, new BigDecimal("1500.00"), false),

            // Savings account interest
            new DemoTransaction(30, "Savings", "Bank Interest", null, new BigDecimal("5.25"), true),
            new DemoTransaction(1, "Savings", "Bank Interest", null, new BigDecimal("5.40"), true),

            // Housing
            new DemoTransaction(59, "Checking", "Sunrise Apartments", "Rent / Mortgage", new BigDecimal("-1200.00"), true),
            new DemoTransaction(29, "Checking", "Sunrise Apartments", "Rent / Mortgage", new BigDecimal("-1200.00"), true),
            new DemoTransaction(55, "Checking", "City Power & Water", "Utilities", new BigDecimal("-85.00"), true),
            new DemoTransaction(25, "Checking", "City Power & Water", "Utilities", new BigDecimal("-92.00"), true),
            new DemoTransaction(50, "Checking", "Comcast", "Internet", new BigDecimal("-60.00"), true),
            new DemoTransaction(20, "Checking", "Comcast", "Internet", new BigDecimal("-60.00"), true),
            new DemoTransaction(45, "Checking", "State Farm", "Home Insurance", new BigDecimal("-40.00"), true),
            new DemoTransaction(15, "Checking", "State Farm", "Home Insurance", new BigDecimal("-40.00"), true),

            // Food
            new DemoTransaction(56, "Checking", "Trader Joe's", "Groceries", new BigDecimal("-85.00"), true),
            new DemoTransaction(48, "Checking", "Safeway", "Groceries", new BigDecimal("-62.40"), true),
            new DemoTransaction(40, "Checking", "Trader Joe's", "Groceries", new BigDecimal("-91.15"), true),
            new DemoTransaction(33, "Checking", "Whole Foods", "Groceries", new BigDecimal("-110.00"), true),
            new DemoTransaction(21, "Checking", "Safeway", "Groceries", new BigDecimal("-70.25"), true),
            new DemoTransaction(10, "Checking", "Trader Joe's", "Groceries", new BigDecimal("-88.60"), false),
            new DemoTransaction(3, "Checking", "Safeway", "Groceries", new BigDecimal("-54.30"), false),
            new DemoTransaction(52, "Checking", "Chipotle", "Dining Out", new BigDecimal("-14.50"), true),
            new DemoTransaction(38, "Checking", "Local Pizza Co", "Dining Out", new BigDecimal("-32.00"), true),
            new DemoTransaction(27, "Checking", "Sushi House", "Dining Out", new BigDecimal("-58.75"), true),
            new DemoTransaction(12, "Checking", "Chipotle", "Dining Out", new BigDecimal("-16.25"), false),
            new DemoTransaction(5, "Checking", "Coffee & Co", "Dining Out", new BigDecimal("-9.80"), false),

            // Transport
            new DemoTransaction(54, "Checking", "Shell", "Gas", new BigDecimal("-42.00"), true),
            new DemoTransaction(42, "Checking", "Chevron", "Gas", new BigDecimal("-38.50"), true),
            new DemoTransaction(28, "Checking", "Shell", "Gas", new BigDecimal("-45.75"), true),
            new DemoTransaction(14, "Checking", "Chevron", "Gas", new BigDecimal("-40.20"), false),
            new DemoTransaction(4, "Checking", "Shell", "Gas", new BigDecimal("-43.10"), false),
            new DemoTransaction(47, "Checking", "Geico", "Car Insurance", new BigDecimal("-95.00"), true),
            new DemoTransaction(17, "Checking", "Geico", "Car Insurance", new BigDecimal("-95.00"), true),
            new DemoTransaction(35, "Checking", "Metro Transit", "Public Transit", new BigDecimal("-50.00"), true),
            new DemoTransaction(8, "Checking", "Metro Transit", "Public Transit", new BigDecimal("-50.00"), false)
        );
    }

    private void createTransactions(Map<String, UUID> accountIdsByName, Map<String, UUID> categoryIdsByName) {
        LocalDate today = LocalDate.now();
        for (DemoTransaction t : transactionPlan()) {
            UUID accountId = accountIdsByName.get(t.accountName());
            UUID categoryId = t.categoryName() == null ? null : categoryIdsByName.get(t.categoryName());
            transactionService.create(new TransactionRequest(
                accountId, today.minusDays(t.daysAgo()), t.payee(), categoryId, t.amount(), null, t.cleared()
            ), demoUserSub);
        }
    }

    // --- budget allocations for the current month ---

    private void createAllocations(Map<String, UUID> categoryIdsByName) {
        Map<String, String> recurringBills = Map.ofEntries(
            Map.entry("Rent / Mortgage", "1200.00"),
            Map.entry("Utilities", "90.00"),
            Map.entry("Internet", "60.00"),
            Map.entry("Home Insurance", "40.00"),
            Map.entry("Groceries", "400.00"),
            Map.entry("Dining Out", "150.00"),
            Map.entry("Gas", "150.00"),
            Map.entry("Car Insurance", "95.00"),
            Map.entry("Public Transit", "60.00")
        );
        Map<String, String> savingsGoals = Map.ofEntries(
            Map.entry("Emergency Fund", "200.00"),
            Map.entry("Retirement", "300.00"),
            Map.entry("Goals", "100.00")
        );

        // transactionPlan() can span more than one prior month (its oldest entry is currently 59
        // days back), so budget every month it actually touches — derived from the plan itself
        // rather than hardcoded, so this can't drift out of sync if the plan's date range changes
        // later. Otherwise an earlier month's spending has no allocation behind it and every
        // category's cumulative Available reads as overspent.
        //
        // Only the recurring bills get backdated this way — they're what the transaction history
        // needs covered. The savings categories have no transaction history to reconcile against,
        // so backdating them too would just eat into Ready to Assign for no reason; they're
        // assigned once, for the current month only.
        int maxDaysAgo = transactionPlan().stream().mapToInt(DemoTransaction::daysAgo).max().orElse(0);
        YearMonth earliestMonth = YearMonth.from(LocalDate.now().minusDays(maxDaysAgo));
        YearMonth currentMonth = YearMonth.now();
        for (YearMonth month = earliestMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            assignAll(recurringBills, categoryIdsByName, month);
        }
        assignAll(savingsGoals, categoryIdsByName, currentMonth);
    }

    private void assignAll(Map<String, String> assignedByCategory, Map<String, UUID> categoryIdsByName, YearMonth month) {
        assignedByCategory.forEach((categoryName, assigned) ->
            budgetService.upsertAllocation(new AllocationRequest(
                categoryIdsByName.get(categoryName), month.toString(), new BigDecimal(assigned)
            ), demoUserSub)
        );
    }

    // --- goals: one REFILL (keep a buffer topped up), one ACCUMULATE (save toward a target) ---

    private void createGoals(Map<String, UUID> categoryIdsByName) {
        goalService.createGoal(new GoalRequest(
            categoryIdsByName.get("Emergency Fund"), new BigDecimal("3000.00"), 1, RolloverType.REFILL
        ), demoUserSub);
        goalService.createGoal(new GoalRequest(
            categoryIdsByName.get("Goals"), new BigDecimal("1000.00"), 15, RolloverType.ACCUMULATE
        ), demoUserSub);
    }
}
