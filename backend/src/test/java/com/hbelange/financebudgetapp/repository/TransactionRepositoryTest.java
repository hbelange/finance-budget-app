package com.hbelange.financebudgetapp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

// Hint: @DataJpaTest loads only the JPA slice of the application (entities, repositories, H2 in-memory DB).
// No controllers or services are loaded. The database is reset between tests automatically.
//
// Caution: the entity uses @Column(columnDefinition = "uuid") which is PostgreSQL syntax.
// H2 in PostgreSQL-compatibility mode should handle it. If you see schema errors, add this
// to src/test/resources/application-test.properties (and annotate with @ActiveProfiles("test")):
//   spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
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
        // Hint: persist two accounts and a few transactions so you can test filtering.
        // Use accountRepository.save(...) to persist — @DataJpaTest wires the real repository.
        // Example:
        //   accountA = new Account(); accountA.setName("A"); accountA.setType(AccountType.CHECKING);
        //   accountA = accountRepository.save(accountA);
        //
        // Then persist transactions with different accounts and dates so filter tests are meaningful.
        // E.g., two transactions for accountA in January, one for accountB in February.
    }

    @Test
    void findByAccountId_returnsOnlyTransactionsForThatAccount() {
        // Hint: call transactionRepository.findByAccountId(accountA.getId(), page).
        // Assert that all returned transactions belong to accountA (check getContent() list).
        // Assert that none belong to accountB.
    }

    @Test
    void findByDateBetween_returnsTransactionsInRange() {
        // Hint: call findByDateBetween with a LocalDate range that covers some but not all transactions.
        // Assert only transactions within that date range appear in the result.
    }

    @Test
    void findByMonth_returnsTransactionsForThatMonth() {
        // Hint: findByMonth(YearMonth.of(2026, 1), page) is a default method — it calls findByDateBetween.
        // Call it and assert only January transactions are returned.
    }

    @Test
    void findByAccountIdBetween_returnsTransactionsForAccountInRange() {
        // Hint: use findByAccountIdBetween with accountA's ID and a date range.
        // Assert all results belong to accountA AND fall within the date range.
    }

    @Test
    void findByAccountIdAndMonth_returnsTransactionsForAccountAndMonth() {
        // Hint: findByAccountIdAndMonth is a default method delegating to findByAccountIdBetween.
        // Call it with accountA.getId() and YearMonth.of(2026, 1).
        // Assert results belong to accountA and are in January.
    }

    @Test
    void findByAccountId_returnsEmpty_whenNoTransactionsForAccount() {
        // Hint: save a fresh account with no transactions.
        // Call findByAccountId with that new account's ID.
        // Assert getContent() is empty.
    }
}
