package com.hbelange.financebudgetapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbelange.financebudgetapp.dto.TransactionRequest;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    // Shared test data — two accounts, three transactions across two months
    private UUID accountAId;
    private UUID accountBId;
    private Account accountA;
    private Account accountB;

    // accountA, Jan 2026
    private Transaction txA_jan;
    // accountA, Feb 2026
    private Transaction txA_feb;
    // accountB, Jan 2026
    private Transaction txB_jan;

    @BeforeEach
    void setUp() {
        accountAId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        accountBId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

        accountA = new Account();
        accountA.setId(accountAId);

        accountB = new Account();
        accountB.setId(accountBId);

        txA_jan = new Transaction();
        txA_jan.setId(UUID.fromString("11111111-0000-0000-0000-000000000001"));
        txA_jan.setAccount(accountA);
        txA_jan.setDate(LocalDate.of(2026, 1, 15));
        txA_jan.setAmount(new BigDecimal("50.00"));
        txA_jan.setCleared(false);

        txA_feb = new Transaction();
        txA_feb.setId(UUID.fromString("11111111-0000-0000-0000-000000000002"));
        txA_feb.setAccount(accountA);
        txA_feb.setDate(LocalDate.of(2026, 2, 10));
        txA_feb.setAmount(new BigDecimal("75.00"));
        txA_feb.setCleared(true);

        txB_jan = new Transaction();
        txB_jan.setId(UUID.fromString("22222222-0000-0000-0000-000000000001"));
        txB_jan.setAccount(accountB);
        txB_jan.setDate(LocalDate.of(2026, 1, 20));
        txB_jan.setAmount(new BigDecimal("30.00"));
        txB_jan.setCleared(false);
    }

    // --- findAll ---

    @Test
    void findAll_noFilters_returnsAllTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findAll(pageable))
            .thenReturn(new PageImpl<>(List.of(txA_jan, txA_feb, txB_jan)));

        Page<TransactionDTO> result = transactionService.findAll(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
            .extracting(TransactionDTO::accountId)
            .containsExactlyInAnyOrder(accountAId, accountAId, accountBId);
    }

    @Test
    void findAll_withAccountId_returnsOnlyThatAccountsTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByAccountId(accountAId, pageable))
            .thenReturn(new PageImpl<>(List.of(txA_jan, txA_feb)));

        Page<TransactionDTO> result = transactionService.findAll(accountAId, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(TransactionDTO::accountId)
            .containsOnly(accountAId);
        assertThat(result.getContent())
            .extracting(TransactionDTO::accountId)
            .doesNotContain(accountBId);
    }

    @Test
    void findAll_withMonth_returnsOnlyThatMonthsTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByMonth(YearMonth.of(2026, 1), pageable))
            .thenReturn(new PageImpl<>(List.of(txA_jan, txB_jan)));

        Page<TransactionDTO> result = transactionService.findAll(null, YearMonth.of(2026, 1), pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(TransactionDTO::date)
            .allMatch(d -> YearMonth.from(d).equals(YearMonth.of(2026, 1)));
        assertThat(result.getContent())
            .extracting(TransactionDTO::date)
            .doesNotContain(LocalDate.of(2026, 2, 10));
    }

    @Test
    void findAll_withAccountIdAndMonth_returnsOnlyMatchingTransaction() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByAccountIdAndMonth(accountAId, YearMonth.of(2026, 1), pageable))
            .thenReturn(new PageImpl<>(List.of(txA_jan)));

        Page<TransactionDTO> result = transactionService.findAll(accountAId, YearMonth.of(2026, 1), pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        TransactionDTO dto = result.getContent().get(0);
        assertThat(dto.accountId()).isEqualTo(accountAId);
        assertThat(dto.date()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(dto.amount()).isEqualByComparingTo("50.00");
    }

    // --- create ---

    @Test
    void create_savesAndReturnsDto() {
        TransactionRequest req = new TransactionRequest(
            accountAId, LocalDate.of(2026, 1, 15), "Grocery Store", null,
            new BigDecimal("50.00"), null, false
        );
        when(accountRepository.findById(accountAId)).thenReturn(Optional.of(accountA));
        when(transactionRepository.save(any())).thenReturn(txA_jan);

        TransactionDTO result = transactionService.create(req);

        assertThat(result.accountId()).isEqualTo(accountAId);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void create_throwsException_whenAccountNotFound() {
        TransactionRequest req = new TransactionRequest(
            accountAId, LocalDate.of(2026, 1, 15), null, null,
            new BigDecimal("50.00"), null, false
        );
        when(accountRepository.findById(accountAId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
            .hasMessageContaining("Account not found");
    }

    // --- update ---

    @Test
    void update_updatesAndReturnsDto_whenAccountUnchanged() {
        UUID txId = txA_jan.getId();
        TransactionRequest req = new TransactionRequest(
            accountAId, LocalDate.of(2026, 1, 20), "Updated Payee", null,
            new BigDecimal("60.00"), null, true
        );
        txA_jan.setPayee("Updated Payee");
        txA_jan.setAmount(new BigDecimal("60.00"));
        txA_jan.setCleared(true);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(txA_jan));
        when(transactionRepository.save(any())).thenReturn(txA_jan);

        TransactionDTO result = transactionService.update(txId, req);

        assertThat(result.accountId()).isEqualTo(accountAId);
        assertThat(result.amount()).isEqualByComparingTo("60.00");
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void update_updatesAndReturnsDto_whenAccountChanges() {
        UUID txId = txA_jan.getId();
        // txA_jan starts with accountA; request targets accountB — service must re-fetch the account
        TransactionRequest req = new TransactionRequest(
            accountBId, LocalDate.of(2026, 1, 15), null, null,
            new BigDecimal("50.00"), null, false
        );
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(txA_jan));
        when(accountRepository.findById(accountBId)).thenReturn(Optional.of(accountB));
        when(transactionRepository.save(any())).thenReturn(txA_jan);

        TransactionDTO result = transactionService.update(txId, req);

        assertThat(result.accountId()).isEqualTo(accountBId);
    }

    @Test
    void update_throwsException_whenTransactionNotFound() {
        UUID unknownId = UUID.randomUUID();
        TransactionRequest req = new TransactionRequest(
            accountAId, LocalDate.of(2026, 1, 15), null, null,
            new BigDecimal("50.00"), null, false
        );
        when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(unknownId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
            .hasMessageContaining("Transaction not found");
    }

    @Test
    void update_throwsException_whenNewAccountNotFound() {
        UUID txId = txA_jan.getId();
        TransactionRequest req = new TransactionRequest(
            accountBId, LocalDate.of(2026, 1, 15), null, null,
            new BigDecimal("50.00"), null, false
        );
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(txA_jan));
        when(accountRepository.findById(accountBId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(txId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
            .hasMessageContaining("Account not found");
    }

    // --- delete ---

    @Test
    void delete_deletesTransaction() {
        UUID txId = txA_jan.getId();

        transactionService.delete(txId);

        verify(transactionRepository).deleteById(txId);
    }

    @Test
    void delete_doesNotThrow_whenTransactionNotFound() {
        UUID unknownId = UUID.randomUUID();

        // deleteById on a non-existent ID is a no-op — no exception expected
        transactionService.delete(unknownId);

        verify(transactionRepository).deleteById(unknownId);
    }
}
