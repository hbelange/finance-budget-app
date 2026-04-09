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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account accountA;
    private Account accountB;

    private final Pageable page = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        accountA = new Account();
        accountA.setName("Account A");
        accountA.setType(AccountType.CHECKING);
        accountA = accountRepository.save(accountA);

        accountB = new Account();
        accountB.setName("Account B");
        accountB.setType(AccountType.SAVINGS);
        accountB = accountRepository.save(accountB);

        saveTransaction(accountA, LocalDate.of(2026, 1, 1), 50.0);
        saveTransaction(accountA, LocalDate.of(2026, 1, 10), 100.00);
        saveTransaction(accountA, LocalDate.of(2026, 1, 20), -30.00);
        saveTransaction(accountB, LocalDate.of(2026, 2, 5), 200.00);    
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
        accountC = accountRepository.save(accountC);

        Page<Transaction> result = transactionRepository.findByAccountId(accountC.getId(), page);
        assertTrue(result.getContent().isEmpty());
    }

    private Transaction saveTransaction(Account acct, LocalDate date, Double amount) {
        Transaction transaction = new Transaction();
        transaction.setAccount(acct);
        transaction.setDate(date);
        transaction.setAmount(new BigDecimal(amount));
        return transactionRepository.save(transaction);
    }
}
