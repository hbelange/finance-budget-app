package com.hbelange.financebudgetapp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

// Hint: @DataJpaTest loads only the JPA layer with an in-memory database.
// The same H2 compatibility note from TransactionRepositoryTest applies here.
@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        // Hint: save an Account so we have a real ID to query against.
        // Remember to reassign the result of save() — it returns the persisted entity with the generated ID set.
        // Example:
        //   account = new Account();
        //   account.setName("My Account");
        //   account.setType(AccountType.CHECKING);
        //   account = accountRepository.save(account);
    }

    // --- findBalanceById ---

    @Test
    void findBalanceById_returnsZero_whenNoTransactions() {
        // Hint: don't save any transactions for the account.
        // Call accountRepository.findBalanceById(account.getId()).
        // The query uses COALESCE(..., 0), so it should return 0, not null.
        // Use assertEquals(BigDecimal.ZERO, result) or check compareTo(BigDecimal.ZERO) == 0
        // (BigDecimal equality is scale-sensitive — compareTo is safer).
    }

    @Test
    void findBalanceById_returnsSumOfTransactions() {
        // Hint: save two transactions for the account — e.g. +100.00 and -30.00.
        // A transaction needs: account, date, amount (the others are nullable).
        // Assert findBalanceById returns 70.00.
        // Use BigDecimal compareTo instead of assertEquals to avoid scale mismatches.
    }

    @Test
    void findBalanceById_ignoresTransactionsFromOtherAccounts() {
        // Hint: save a second account and a transaction for it.
        // Call findBalanceById on the first account and assert it is still 0 (or only sums its own transactions).
        // This verifies the WHERE clause is correct.
    }

    // --- existsTransactionsByAccountId ---

    @Test
    void existsTransactionsByAccountId_returnsFalse_whenNoTransactions() {
        // Hint: no transactions saved in this test.
        // Assert accountRepository.existsTransactionsByAccountId(account.getId()) is false.
    }

    @Test
    void existsTransactionsByAccountId_returnsTrue_whenTransactionExists() {
        // Hint: save one transaction for the account.
        // Assert existsTransactionsByAccountId returns true.
    }

    @Test
    void existsTransactionsByAccountId_returnsFalse_forOtherAccount() {
        // Hint: save a second account and a transaction for it.
        // Call existsTransactionsByAccountId on the first (empty) account.
        // Assert false — verifies we're filtering by the correct accountId.
    }

    // Helper to create and save a transaction for an account
    private Transaction saveTransaction(Account acct, String amount) {
        // Hint: use this helper in your tests to reduce boilerplate.
        // Build a Transaction, set acct, date, and amount, then call transactionRepository.save(...).
        // Return the saved transaction.
        throw new UnsupportedOperationException("Implement this helper");
    }
}
