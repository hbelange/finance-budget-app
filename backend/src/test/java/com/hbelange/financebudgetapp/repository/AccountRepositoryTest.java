package com.hbelange.financebudgetapp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        account = new Account();
        account.setName("My Account");
        account.setType(AccountType.CHECKING);
        account = accountRepository.save(account);
    }

    // --- findBalanceById ---

    @Test
    void findBalanceById_returnsZero_whenNoTransactions() {
        // Hint: don't save any transactions for the account.
        // Call accountRepository.findBalanceById(account.getId()).
        // The query uses COALESCE(..., 0), so it should return 0, not null.
        // Use assertEquals(BigDecimal.ZERO, result) or check compareTo(BigDecimal.ZERO) == 0
        // (BigDecimal equality is scale-sensitive — compareTo is safer).
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertEquals(BigDecimal.ZERO, balance);
    }

    @Test
    void findBalanceById_returnsSumOfTransactions() {
        saveTransaction(account, 100.00);
        saveTransaction(account, -30.00);
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertEquals(balance, new BigDecimal("70.00")); 
    }

    @Test
    void findBalanceById_ignoresTransactionsFromOtherAccounts() {
        // Hint: save a second account and a transaction for it.
        // Call findBalanceById on the first account and assert it is still 0 (or only sums its own transactions).
        // This verifies the WHERE clause is correct.
        Account otherAccount = new Account();
        otherAccount.setName("Other Account");
        otherAccount.setType(AccountType.SAVINGS);
        otherAccount = accountRepository.save(otherAccount);

        saveTransaction(otherAccount, 50.00);
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertEquals(BigDecimal.ZERO, balance); // First account has no transactions, so balance should be zero, not 50.
    }

    // --- existsTransactionsByAccountId ---

    @Test
    void existsTransactionsByAccountId_returnsFalse_whenNoTransactions() {
        assertFalse(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    @Test
    void existsTransactionsByAccountId_returnsTrue_whenTransactionExists() {
        saveTransaction(account, 20.00);
        assertTrue(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    @Test
    void existsTransactionsByAccountId_returnsFalse_forOtherAccount() {
        Account otherAccount = new Account();
        otherAccount.setName("Other Account");
        otherAccount.setType(AccountType.SAVINGS);
        otherAccount = accountRepository.save(otherAccount);

        saveTransaction(otherAccount, 50.00);
        assertFalse(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    // Helper to create and save a transaction for an account
    private Transaction saveTransaction(Account acct, Double amount) {
        // Hint: use this helper in your tests to reduce boilerplate.
        // Build a Transaction, set acct, date, and amount, then call transactionRepository.save(...).
        // Return the saved transaction.
        Transaction transaction = new Transaction();
        transaction.setAccount(acct);
        transaction.setDate(LocalDate.now());
        transaction.setAmount(new BigDecimal(amount));
        return transactionRepository.save(transaction);
    }
}
