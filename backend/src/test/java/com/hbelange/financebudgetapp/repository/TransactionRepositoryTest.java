package com.hbelange.financebudgetapp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account accountA;
    private Account accountB;
    private Account ccAccount;

    private final Pageable page = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        accountA = new Account();
        accountA.setName("Account A");
        accountA.setType(AccountType.CHECKING);
        accountA.setUserSub("auth0|test-user");
        accountA = accountRepository.save(accountA);

        accountB = new Account();
        accountB.setName("Account B");
        accountB.setType(AccountType.SAVINGS);
        accountB.setUserSub("auth0|test-user");
        accountB = accountRepository.save(accountB);

        ccAccount = new Account();
        ccAccount.setName("My Visa");
        ccAccount.setType(AccountType.CREDIT_CARD);
        ccAccount.setUserSub("auth0|test-user");
        ccAccount = accountRepository.save(ccAccount);

        saveTransaction(accountA, LocalDate.of(2026, 1, 1), "50.00");
        saveTransaction(accountA, LocalDate.of(2026, 1, 10), "100.00");
        saveTransaction(accountA, LocalDate.of(2026, 1, 20), "-30.00");
        saveTransaction(accountB, LocalDate.of(2026, 2, 5), "200.00");
    }

    @Test
    void findByAccountId_returnsOnlyTransactionsForThatAccount() {
        Page<Transaction> result = transactionRepository.findByAccountId(accountA.getId(), page);
        assertTrue(result.getContent().stream().allMatch(t -> t.getAccount().getId().equals(accountA.getId())));
        assertFalse(result.getContent().stream().anyMatch(t -> t.getAccount().getId().equals(accountB.getId())));
    }

    @Test
    void findByDateBetween_returnsTransactionsInRange() {
        Page<Transaction> result = transactionRepository.findByDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), page);
        assertTrue(result.getContent().stream().allMatch(t -> t.getDate().isAfter(LocalDate.of(2025, 12, 31)) && t.getDate().isBefore(LocalDate.of(2026, 2, 1))));
    }

    @Test
    void findByMonth_returnsTransactionsForThatMonth() {
        Page<Transaction> result = transactionRepository.findByMonth(YearMonth.of(2026, 1), page);
        assertTrue(result.getContent().stream().allMatch(t -> YearMonth.from(t.getDate()).equals(YearMonth.of(2026, 1))));
    }

    @Test
    void findByAccountIdBetween_returnsTransactionsForAccountInRange() {
        Page<Transaction> result = transactionRepository.findByAccountIdBetween(accountA.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), page);
        assertTrue(result.getContent().stream().allMatch(t -> t.getAccount().getId().equals(accountA.getId()) && t.getDate().isAfter(LocalDate.of(2025, 12, 31)) && t.getDate().isBefore(LocalDate.of(2026, 2, 1))));

    }

    @Test
    void findByAccountIdAndMonth_returnsTransactionsForAccountAndMonth() {
        Page<Transaction> result = transactionRepository.findByAccountIdAndMonth(accountA.getId(), YearMonth.of(2026, 1), page);
        assertTrue(result.getContent().stream().allMatch(t -> t.getAccount().getId().equals(accountA.getId()) && YearMonth.from(t.getDate()).equals(YearMonth.of(2026, 1))));
    }

    @Test
    void findByAccountId_returnsEmpty_whenNoTransactionsForAccount() {
        Account accountC = new Account();
        accountC.setName("Account C");
        accountC.setType(AccountType.CHECKING);
        accountC.setUserSub("auth0|test-user");
        accountC = accountRepository.save(accountC);

        Page<Transaction> result = transactionRepository.findByAccountId(accountC.getId(), page);
        assertTrue(result.getContent().isEmpty());
    }

    // --- findByAccountUserSub ---

    @Test
    void findByAccountUserSub_returnsAllTransactionsForUser() {
        Page<Transaction> result = transactionRepository.findByAccountUserSub("auth0|test-user", page);
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void findByAccountUserSub_returnsEmpty_forUnknownUser() {
        Page<Transaction> result = transactionRepository.findByAccountUserSub("auth0|unknown", page);
        assertThat(result).isEmpty();
    }

    // --- findByUserSubAndDateBetween ---

    @Test
    void findByUserSubAndDateBetween_returnsTransactionsInRange() {
        Page<Transaction> result = transactionRepository.findByUserSubAndDateBetween(
            "auth0|test-user", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), page);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).allMatch(t -> t.getAccount().getUserSub().equals("auth0|test-user"));
    }

    @Test
    void findByUserSubAndDateBetween_excludesOutOfRangeTransactions() {
        Page<Transaction> result = transactionRepository.findByUserSubAndDateBetween(
            "auth0|test-user", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), page);
        assertThat(result.getContent()).noneMatch(t -> t.getDate().isAfter(LocalDate.of(2026, 1, 31)));
    }

    // --- sumNetWorth ---

    @Test
    void sumNetWorth_returnsSumOfAllTransactions() {
        BigDecimal result = transactionRepository.sumNetWorth("auth0|test-user");
        assertThat(result).isEqualByComparingTo("320.00"); // 50 + 100 - 30 + 200
    }

    @Test
    void sumNetWorth_returnsZero_forUnknownUser() {
        BigDecimal result = transactionRepository.sumNetWorth("auth0|unknown");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumNetWorth_excludesOtherUsersTransactions() {
        Account other = new Account();
        other.setName("Other Account");
        other.setType(AccountType.CHECKING);
        other.setUserSub("auth0|other-user");
        other = accountRepository.save(other);
        saveTransaction(other, LocalDate.of(2026, 1, 1), "9999.00");

        BigDecimal result = transactionRepository.sumNetWorth("auth0|test-user");
        assertThat(result).isEqualByComparingTo("320.00");
    }

    // --- sumIncomeForMonth ---

    @Test
    void sumIncomeForMonth_returnsSumOfPositiveAmounts() {
        BigDecimal result = transactionRepository.sumIncomeForMonth(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("150.00"); // 50 + 100
    }

    @Test
    void sumIncomeForMonth_returnsZero_whenNoIncome() {
        BigDecimal result = transactionRepository.sumIncomeForMonth(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- sumSpentForMonth ---

    @Test
    void sumSpentForMonth_returnsSumOfNegativeAmounts() {
        BigDecimal result = transactionRepository.sumSpentForMonth(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("-30.00");
    }

    @Test
    void sumSpentForMonth_returnsZero_whenNoExpenses() {
        BigDecimal result = transactionRepository.sumSpentForMonth(
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "auth0|test-user");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- sumNetUpToDate ---

    @Test
    void sumNetUpToDate_returnsCumulativeSumUpToDate() {
        BigDecimal result = transactionRepository.sumNetUpToDate(
            LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("120.00"); // 50 + 100 - 30, excludes Feb
    }

    // --- findExpenseByCategoryForMonth ---

    @Test
    void findExpenseByCategoryForMonth_groupsExpensesByCategory() {
        UUID catId = java.util.UUID.randomUUID();
        Transaction t = new Transaction();
        t.setAccount(accountA);
        t.setDate(LocalDate.of(2026, 1, 5));
        t.setAmount(new BigDecimal("-80.00"));
        t.setCategoryId(catId);
        transactionRepository.save(t);

        List<com.hbelange.financebudgetapp.dto.CategorySpent> result =
            transactionRepository.findExpenseByCategoryForMonth(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "auth0|test-user");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryId()).isEqualTo(catId);
        assertThat(result.get(0).spent()).isEqualByComparingTo("-80.00");
    }

    @Test
    void findExpenseByCategoryForMonth_excludesIncomeTransactions() {
        UUID catId = java.util.UUID.randomUUID();
        Transaction income = new Transaction();
        income.setAccount(accountA);
        income.setDate(LocalDate.of(2026, 1, 5));
        income.setAmount(new BigDecimal("200.00"));
        income.setCategoryId(catId);
        transactionRepository.save(income);

        List<com.hbelange.financebudgetapp.dto.CategorySpent> result =
            transactionRepository.findExpenseByCategoryForMonth(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "auth0|test-user");

        assertThat(result).isEmpty();
    }

    // --- sumNetExcludingCCPurchases ---

    @Test
    void sumNetExcludingCCPurchases_includesNormalTransactions() {
        // accountA (CHECKING): 50 + 100 - 30 = 120, through Jan 31 (excludes Feb accountB)
        BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
            LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("120.00");
    }

    @Test
    void sumNetExcludingCCPurchases_excludesCCPurchasesWithCategory() {
        UUID catId = UUID.randomUUID();
        // CC transaction with category (expense) — must be excluded
        Transaction ccPurchase = new Transaction();
        ccPurchase.setAccount(ccAccount);
        ccPurchase.setDate(LocalDate.of(2026, 1, 15));
        ccPurchase.setAmount(new BigDecimal("-50.00"));
        ccPurchase.setCategoryId(catId);
        transactionRepository.save(ccPurchase);

        BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
            LocalDate.of(2026, 1, 31), "auth0|test-user");
        // 120 from accountA; -50 CC purchase excluded → still 120
        assertThat(result).isEqualByComparingTo("120.00");
    }

    @Test
    void sumNetExcludingCCPurchases_includesCCTransfersEvenWithCategory() {
        UUID catId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        // CC transaction WITH category AND transferId — transferId breaks the exclusion rule, so included
        Transaction ccTransfer = new Transaction();
        ccTransfer.setAccount(ccAccount);
        ccTransfer.setDate(LocalDate.of(2026, 1, 20));
        ccTransfer.setAmount(new BigDecimal("100.00"));
        ccTransfer.setCategoryId(catId);
        ccTransfer.setTransferId(transferId);
        transactionRepository.save(ccTransfer);

        BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
            LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("220.00"); // 120 + 100
    }

    @Test
    void sumNetExcludingCCPurchases_includesCCTransactionWithoutCategory() {
        // CC transaction with no category (e.g. a transfer leg) — NOT a CC purchase, so included
        Transaction ccUncategorized = new Transaction();
        ccUncategorized.setAccount(ccAccount);
        ccUncategorized.setDate(LocalDate.of(2026, 1, 15));
        ccUncategorized.setAmount(new BigDecimal("100.00"));
        // no categoryId, no transferId
        transactionRepository.save(ccUncategorized);

        BigDecimal result = transactionRepository.sumNetExcludingCCPurchases(
            LocalDate.of(2026, 1, 31), "auth0|test-user");
        assertThat(result).isEqualByComparingTo("220.00"); // 120 + 100
    }

    // --- sumForAccount ---

    @Test
    void sumForAccount_returnsSumOfAllTransactionsUpToDate() {
        // accountA has: 50 (Jan 1), 100 (Jan 10), -30 (Jan 20) = 120 through Jan 31
        BigDecimal result = transactionRepository.sumForAccount(
            accountA.getId(), LocalDate.of(2026, 1, 31));
        assertThat(result).isEqualByComparingTo("120.00");
    }

    @Test
    void sumForAccount_respectsCutoffDate() {
        // Only Jan 1 transaction (50) is on or before Jan 1
        BigDecimal result = transactionRepository.sumForAccount(
            accountA.getId(), LocalDate.of(2026, 1, 1));
        assertThat(result).isEqualByComparingTo("50.00");
    }

    @Test
    void sumForAccount_returnsZero_whenNoTransactions() {
        BigDecimal result = transactionRepository.sumForAccount(
            ccAccount.getId(), LocalDate.of(2026, 1, 31));
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Transaction saveTransaction(Account acct, LocalDate date, String amount) {
        Transaction transaction = new Transaction();
        transaction.setAccount(acct);
        transaction.setDate(date);
        transaction.setAmount(new BigDecimal(amount));
        return transactionRepository.save(transaction);
    }
}
