package com.hbelange.financebudgetapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Hint: @ExtendWith(MockitoExtension.class) tells JUnit to activate Mockito.
// This means @Mock and @InjectMocks annotations will be processed automatically.
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    // Hint: @Mock creates a fake (mock) version of the repository.
    // The real database is never touched. You control what these return in each test.
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    // Hint: @InjectMocks creates a real TransactionService and injects the mocks above into it.
    @InjectMocks
    private TransactionService transactionService;

    // Shared test data — set up once before each test in setUp()
    private UUID accountId;
    private UUID transactionId;
    private Account account;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        // Hint: Build a minimal Account and Transaction for reuse across tests.
        // Use UUID.randomUUID() or fixed UUIDs — fixed ones make assertion failures easier to read.
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        account = new Account();
        account.setId(accountId);
        // Hint: set the account name and type here too

        transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setAccount(account);
        transaction.setDate(LocalDate.of(2026, 1, 15));
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setCleared(false);
    }

    // --- findAll ---

    @Test
    void findAll_noFilters_returnsAllTransactions() {
        // Hint: when no accountId or month is passed, the service calls transactionRepository.findAll(pageable).
        // Use Mockito's when(...).thenReturn(...) to return a Page containing your test transaction.
        // Hint: to create a Page, use: new PageImpl<>(List.of(transaction))
        // Then call transactionService.findAll(null, null, pageable) and assert the result.
    }

    @Test
    void findAll_withAccountId_returnsFilteredByAccount() {
        // Hint: when only accountId is non-null, the service calls findByAccountId(accountId, pageable).
        // Mock that method and verify the result maps to a TransactionDTO with the correct accountId.
    }

    @Test
    void findAll_withMonth_returnsFilteredByMonth() {
        // Hint: when only month is non-null, the service calls findByMonth(month, pageable).
        // Use YearMonth.of(2026, 1) as your test month.
        // Remember: findByMonth is a default method — it delegates to findByDateBetween internally.
        // Mock findByDateBetween (the @Query method) rather than findByMonth.
    }

    @Test
    void findAll_withAccountIdAndMonth_returnsFilteredByBoth() {
        // Hint: when both accountId and month are provided, findByAccountIdAndMonth is called.
        // That delegates to findByAccountIdBetween — mock that one.
    }

    // --- create ---

    @Test
    void create_savesAndReturnsDto() {
        // Hint: build a TransactionRequest with all required fields (accountId, date, amount).
        // Use when(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        // and when(transactionRepository.save(any())).thenReturn(transaction).
        // Assert the returned DTO has the expected values (accountId, date, amount).
    }

    @Test
    void create_throwsException_whenAccountNotFound() {
        // Hint: use when(accountRepository.findById(any())).thenReturn(Optional.empty()).
        // The service throws IllegalArgumentException in this case.
        // Use assertThrows(IllegalArgumentException.class, () -> transactionService.create(req)).
        // Check the exception message contains "Account not found".
    }

    // --- update ---

    @Test
    void update_updatesAndReturnsDto_whenAccountUnchanged() {
        // Hint: when the accountId in the request matches the transaction's current account,
        // the service should NOT call accountRepository.findById again.
        // Set up: mock transactionRepository.findById to return the transaction.
        // Pass a request with the same accountId as transaction.getAccount().getId().
        // After calling update, verify accountRepository.findById was never called.
    }

    @Test
    void update_updatesAndReturnsDto_whenAccountChanges() {
        // Hint: when the accountId in the request differs from the current account,
        // the service looks up the new account.
        // Create a second account with a different UUID, mock accountRepository.findById to return it.
        // Assert the returned DTO has the new accountId.
    }

    @Test
    void update_throwsException_whenTransactionNotFound() {
        // Hint: mock transactionRepository.findById to return Optional.empty().
        // Expect IllegalArgumentException with message containing "Transaction not found".
    }

    @Test
    void update_throwsException_whenNewAccountNotFound() {
        // Hint: mock transactionRepository.findById to return the transaction.
        // Use a different accountId in the request.
        // Mock accountRepository.findById to return Optional.empty().
        // Expect IllegalArgumentException with message containing "Account not found".
    }

    // --- delete ---

    @Test
    void delete_deletesTransaction() {
        // Hint: mock transactionRepository.existsById to return true.
        // Call transactionService.delete(transactionId).
        // Verify transactionRepository.deleteById(transactionId) was called exactly once.
    }

    @Test
    void delete_throwsException_whenTransactionNotFound() {
        // Hint: mock transactionRepository.existsById to return false.
        // Expect IllegalArgumentException.
        // Verify deleteById is never called (use verify(..., never())).
    }
}
