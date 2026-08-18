package com.hbelange.financebudgetapp.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetAllocation;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.entity.Goal;
import com.hbelange.financebudgetapp.entity.Transaction;
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
import com.hbelange.financebudgetapp.service.CreditCardService;
import com.hbelange.financebudgetapp.service.GoalService;
import com.hbelange.financebudgetapp.service.TransactionService;

/**
 * Exercises DemoDataService against a real (H2) database with the real service layer wired up
 * by hand, so we verify actual persisted rows rather than mocked interactions.
 */
@DataJpaTest
class DemoDataServiceTest {

    private static final String DEMO_SUB = "auth0|6a83a3c9fbc136f08d2e9e77";
    private static final String OTHER_SUB = "auth0|other-real-user";
    private static final int EXPECTED_TRANSACTION_COUNT = 36;
    private static final int EXPECTED_CATEGORY_COUNT = 12;
    private static final int EXPECTED_ALLOCATION_COUNT = 12;
    private static final int EXPECTED_GOAL_COUNT = 2;

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetAllocationRepository budgetAllocationRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;
    @Autowired private CategoryGroupRepository categoryGroupRepository;
    @Autowired private TestEntityManager em;

    private DemoDataService demoDataService;

    @BeforeEach
    void setUp() {
        // BudgetAllocationRepository.upsert() is a native "INSERT ... ON CONFLICT DO UPDATE"
        // statement — real Postgres syntax, proven against the actual V5 migration constraint in
        // production, but H2 2.3.232 (even in PostgreSQL compatibility mode) does not parse the
        // ON CONFLICT clause at all (verified directly against H2, independent of Hibernate schema
        // generation). No existing test exercises this native query against a real DB; it's always
        // mocked. Rather than touch the production query, delegate to the real repository for this
        // test only, with upsert() swapped for an H2-safe equivalent (delete-then-insert via plain
        // JPA calls), so every other call in this suite still hits the real, unmocked repository.
        // (Mockito's spy() can't wrap this repository — Spring Data's JDK dynamic proxy trips
        // "Failed to unwrap proxied object" — so a plain reflective proxy is used instead.)
        BudgetAllocationRepository budgetAllocationRepositorySpy = (BudgetAllocationRepository) Proxy.newProxyInstance(
            BudgetAllocationRepository.class.getClassLoader(),
            new Class<?>[] {BudgetAllocationRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("upsert")) {
                    UUID categoryId = (UUID) args[0];
                    LocalDate month = (LocalDate) args[1];
                    BigDecimal assigned = (BigDecimal) args[2];
                    budgetAllocationRepository.findAll().stream()
                        .filter(a -> a.getCategoryId().equals(categoryId) && a.getMonth().equals(month))
                        .forEach(budgetAllocationRepository::delete);
                    BudgetAllocation allocation = new BudgetAllocation();
                    allocation.setCategoryId(categoryId);
                    allocation.setMonth(month);
                    allocation.setAssigned(assigned);
                    budgetAllocationRepository.save(allocation);
                    return null;
                }
                return method.invoke(budgetAllocationRepository, args);
            });

        CreditCardService creditCardService =
            new CreditCardService(categoryGroupRepository, budgetCategoryRepository, accountRepository);
        AccountService accountService = new AccountService(accountRepository, creditCardService);
        CategoryService categoryService = new CategoryService(
            categoryGroupRepository, budgetCategoryRepository, budgetAllocationRepositorySpy, accountRepository);
        TransactionService transactionService = new TransactionService(transactionRepository, accountRepository);
        BudgetService budgetService = new BudgetService(
            categoryGroupRepository, budgetCategoryRepository, budgetAllocationRepositorySpy,
            transactionRepository, accountRepository, goalRepository);
        GoalService goalService = new GoalService(goalRepository, budgetCategoryRepository);

        demoDataService = new DemoDataService(
            accountRepository, transactionRepository, budgetAllocationRepositorySpy,
            goalRepository, budgetCategoryRepository, categoryGroupRepository,
            accountService, categoryService, transactionService, budgetService, goalService,
            DEMO_SUB);
    }

    @Test
    void reset_createsTwoAccountsForDemoUser() {
        demoDataService.reset();

        List<Account> accounts = accountRepository.findByUserSub(DEMO_SUB);
        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::getName).containsExactlyInAnyOrder("Checking", "Savings");
    }

    @Test
    void reset_createsFourCategoryGroupsWithExpectedCategories() {
        demoDataService.reset();

        List<CategoryGroup> groups = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(DEMO_SUB);
        assertThat(groups).extracting(CategoryGroup::getName)
            .containsExactly("Housing", "Food", "Transport", "Savings");

        int totalCategories = groups.stream()
            .mapToInt(g -> budgetCategoryRepository.findByGroupOrderBySortOrderAsc(g).size())
            .sum();
        assertThat(totalCategories).isEqualTo(EXPECTED_CATEGORY_COUNT);
    }

    @Test
    void reset_createsTransactionsWithinExpectedRelativeWindowAndCount() {
        demoDataService.reset();

        List<Transaction> txns = transactionRepository.findAll().stream()
            .filter(t -> t.getAccount().getUserSub().equals(DEMO_SUB))
            .toList();

        assertThat(txns).hasSize(EXPECTED_TRANSACTION_COUNT);
        LocalDate today = LocalDate.now();
        assertThat(txns).allSatisfy(t -> {
            assertThat(t.getDate()).isAfterOrEqualTo(today.minusDays(65));
            assertThat(t.getDate()).isBeforeOrEqualTo(today);
        });
        assertThat(txns).anyMatch(t -> t.getAmount().signum() > 0);
        assertThat(txns).anyMatch(t -> t.getAmount().signum() < 0);
        assertThat(txns).anyMatch(t -> Boolean.TRUE.equals(t.getCleared()));
        assertThat(txns).anyMatch(t -> Boolean.FALSE.equals(t.getCleared()));
    }

    @Test
    void reset_createsAllocationsForCurrentMonth() {
        demoDataService.reset();

        LocalDate firstOfMonth = YearMonth.now().atDay(1);
        List<BudgetAllocation> allocations = budgetAllocationRepository.findByMonthAndUserSub(firstOfMonth, DEMO_SUB);

        assertThat(allocations).hasSize(EXPECTED_ALLOCATION_COUNT);
        BigDecimal total = allocations.stream()
            .map(BudgetAllocation::getAssigned)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("2845.00");
    }

    @Test
    void reset_backdatesRecurringBillsButNotSavingsGoalsToEveryMonthTransactionsSpan() {
        demoDataService.reset();

        // Mirrors transactionPlan()'s oldest entry (currently 59 days back) — if that plan's date
        // range ever changes, this constant must move with it or this test stops proving anything.
        LocalDate firstOfEarliestMonth = YearMonth.from(LocalDate.now().minusDays(59)).atDay(1);
        List<BudgetAllocation> allocations =
            budgetAllocationRepository.findByMonthAndUserSub(firstOfEarliestMonth, DEMO_SUB);

        // Only the 9 recurring bills are backdated — Emergency Fund/Retirement/Goals are
        // current-month-only, since they have no transaction history to reconcile against.
        assertThat(allocations).hasSize(9);
        BigDecimal total = allocations.stream()
            .map(BudgetAllocation::getAssigned)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("2245.00");
    }

    @Test
    void reset_createsTwoGoals() {
        demoDataService.reset();

        List<Goal> goals = goalRepository.findByCategory_Group_UserSub(DEMO_SUB);
        assertThat(goals).hasSize(EXPECTED_GOAL_COUNT);
    }

    @Test
    void reset_isIdempotent_noDuplicatesOnSecondCall() {
        demoDataService.reset();
        // In production each reset() call gets its own fresh transaction/persistence context
        // (called from an HTTP-triggered path or the scheduler, never nested inside another).
        // This test chains two calls inside one @DataJpaTest transaction, so flush + clear here
        // to mimic that fresh-session boundary rather than leaving round 1's entities stale in
        // the shared Hibernate session.
        em.flush();
        em.clear();
        demoDataService.reset();

        assertThat(accountRepository.findByUserSub(DEMO_SUB)).hasSize(2);
        assertThat(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(DEMO_SUB)).hasSize(4);

        List<Transaction> txns = transactionRepository.findAll().stream()
            .filter(t -> t.getAccount().getUserSub().equals(DEMO_SUB))
            .toList();
        assertThat(txns).hasSize(EXPECTED_TRANSACTION_COUNT);

        List<BudgetAllocation> allocations =
            budgetAllocationRepository.findByMonthAndUserSub(YearMonth.now().atDay(1), DEMO_SUB);
        assertThat(allocations).hasSize(EXPECTED_ALLOCATION_COUNT);

        assertThat(goalRepository.findByCategory_Group_UserSub(DEMO_SUB)).hasSize(EXPECTED_GOAL_COUNT);
    }

    @Test
    void reset_doesNotTouchOtherUsersData() {
        CategoryGroup otherGroup = new CategoryGroup();
        otherGroup.setName("Other Housing");
        otherGroup.setSortOrder(0);
        otherGroup.setUserSub(OTHER_SUB);
        otherGroup = categoryGroupRepository.save(otherGroup);

        BudgetCategory otherCategory = new BudgetCategory();
        otherCategory.setGroup(otherGroup);
        otherCategory.setName("Other Rent");
        otherCategory.setSortOrder(0);
        otherCategory = budgetCategoryRepository.save(otherCategory);

        Account otherAccount = new Account();
        otherAccount.setName("Other Checking");
        otherAccount.setType(AccountType.CHECKING);
        otherAccount.setUserSub(OTHER_SUB);
        otherAccount = accountRepository.save(otherAccount);

        Transaction otherTxn = new Transaction();
        otherTxn.setAccount(otherAccount);
        otherTxn.setDate(LocalDate.now());
        otherTxn.setPayee("Other Payee");
        otherTxn.setCategoryId(otherCategory.getId());
        otherTxn.setAmount(new BigDecimal("-50.00"));
        otherTxn.setCleared(true);
        transactionRepository.save(otherTxn);

        BudgetAllocation otherAllocation = new BudgetAllocation();
        otherAllocation.setCategoryId(otherCategory.getId());
        otherAllocation.setMonth(YearMonth.now().atDay(1));
        otherAllocation.setAssigned(new BigDecimal("500.00"));
        budgetAllocationRepository.save(otherAllocation);

        Goal otherGoal = new Goal();
        otherGoal.setCategory(otherCategory);
        otherGoal.setAmount(new BigDecimal("1000.00"));
        otherGoal.setDayOfMonth(1);
        otherGoal.setRolloverType(RolloverType.REFILL);
        goalRepository.save(otherGoal);

        CategoryGroup finalOtherGroup = otherGroup;

        demoDataService.reset();

        assertThat(accountRepository.findByUserSub(OTHER_SUB)).hasSize(1);
        assertThat(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(OTHER_SUB)).hasSize(1);
        assertThat(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(finalOtherGroup)).hasSize(1);
        assertThat(transactionRepository.findAll().stream()
            .filter(t -> t.getAccount().getUserSub().equals(OTHER_SUB)).toList()).hasSize(1);
        assertThat(budgetAllocationRepository.findByMonthAndUserSub(YearMonth.now().atDay(1), OTHER_SUB)).hasSize(1);
        assertThat(goalRepository.findByCategory_Group_UserSub(OTHER_SUB)).hasSize(1);
    }
}
