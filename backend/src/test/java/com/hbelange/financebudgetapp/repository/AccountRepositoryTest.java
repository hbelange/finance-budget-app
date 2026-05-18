package com.hbelange.financebudgetapp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        account.setUserSub("auth0|test-user");
        account = accountRepository.save(account);
    }

    // --- findBalanceById ---

    @Test
    void findBalanceById_returnsZero_whenNoTransactions() {
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findBalanceById_returnsSumOfTransactions() {
        saveTransaction(account, "100.00");
        saveTransaction(account, "-30.00");
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertThat(balance).isEqualByComparingTo("70.00");
    }

    @Test
    void findBalanceById_ignoresTransactionsFromOtherAccounts() {
        Account otherAccount = new Account();
        otherAccount.setName("Other Account");
        otherAccount.setType(AccountType.SAVINGS);
        otherAccount.setUserSub("auth0|test-user");
        otherAccount = accountRepository.save(otherAccount);

        saveTransaction(otherAccount, "50.00");
        BigDecimal balance = accountRepository.findBalanceById(account.getId());
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- findByUserSub ---

    @Test
    void findByUserSub_returnsAccountsForThatUser() {
        Account other = new Account();
        other.setName("Other Account");
        other.setType(AccountType.CHECKING);
        other.setUserSub("auth0|other-user");
        accountRepository.save(other);

        List<Account> result = accountRepository.findByUserSub("auth0|test-user");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(account.getId());
    }

    @Test
    void findByUserSub_returnsEmpty_forUnknownUser() {
        List<Account> result = accountRepository.findByUserSub("auth0|unknown");
        assertThat(result).isEmpty();
    }

    // --- findBalancesByUserSub ---

    @Test
    void findBalancesByUserSub_returnsCorrectBalance() {
        saveTransaction(account, "200.00");
        saveTransaction(account, "-50.00");

        List<com.hbelange.financebudgetapp.dto.AccountBalance> balances =
            accountRepository.findBalancesByUserSub("auth0|test-user");

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).balance()).isEqualByComparingTo("150.00");
    }

    @Test
    void findBalancesByUserSub_excludesOtherUsersAccounts() {
        Account other = new Account();
        other.setName("Other Account");
        other.setType(AccountType.CHECKING);
        other.setUserSub("auth0|other-user");
        other = accountRepository.save(other);

        saveTransaction(account, "100.00");
        saveTransaction(other, "999.00");

        List<com.hbelange.financebudgetapp.dto.AccountBalance> balances =
            accountRepository.findBalancesByUserSub("auth0|test-user");

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void findBalancesByUserSub_returnsEmpty_whenNoTransactions() {
        List<com.hbelange.financebudgetapp.dto.AccountBalance> balances =
            accountRepository.findBalancesByUserSub("auth0|test-user");
        assertThat(balances).isEmpty();
    }

    // --- existsTransactionsByAccountId ---

    @Test
    void existsTransactionsByAccountId_returnsFalse_whenNoTransactions() {
        assertFalse(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    @Test
    void existsTransactionsByAccountId_returnsTrue_whenTransactionExists() {
        saveTransaction(account, "20.00");
        assertTrue(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    @Test
    void existsTransactionsByAccountId_returnsFalse_forOtherAccount() {
        Account otherAccount = new Account();
        otherAccount.setName("Other Account");
        otherAccount.setType(AccountType.SAVINGS);
        otherAccount.setUserSub("auth0|test-user");
        otherAccount = accountRepository.save(otherAccount);

        saveTransaction(otherAccount, "50.00");
        assertFalse(accountRepository.existsTransactionsByAccountId(account.getId()));
    }

    private Transaction saveTransaction(Account acct, String amount) {
        Transaction transaction = new Transaction();
        transaction.setAccount(acct);
        transaction.setDate(LocalDate.now());
        transaction.setAmount(new BigDecimal(amount));
        return transactionRepository.save(transaction);
    }
}
