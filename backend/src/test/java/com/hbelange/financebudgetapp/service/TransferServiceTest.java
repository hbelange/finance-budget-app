package com.hbelange.financebudgetapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransferRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferService transferService;

    private static final String USER_SUB = "auth0|test-user";
    private static final String OTHER_SUB = "auth0|other-user";

    private static final UUID FROM_ACCOUNT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TO_ACCOUNT_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID FROM_TX_ID      = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID TO_TX_ID        = UUID.fromString("22222222-0000-0000-0000-000000000002");

    private Account fromAccount;
    private Account toAccount;
    private TransferRequest req;

    @BeforeEach
    void setUp() {
        fromAccount = new Account();
        fromAccount.setId(FROM_ACCOUNT_ID);
        fromAccount.setUserSub(USER_SUB);

        toAccount = new Account();
        toAccount.setId(TO_ACCOUNT_ID);
        toAccount.setUserSub(USER_SUB);

        req = new TransferRequest(
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            LocalDate.of(2026, 1, 15),
            new BigDecimal("100.00"),
            null,
            null
        );
    }

    // --- createTransfer ---

    @Test
    void createTransfer_returnsTwoLinkedDtos() {
        Transaction toSaved  = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  null);
        Transaction fromSaved = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);
        Transaction toFinal  = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);

        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));
        when(transactionRepository.save(any()))
            .thenReturn(toSaved)
            .thenReturn(fromSaved)
            .thenReturn(toFinal);

        List<TransactionDTO> result = transferService.createTransfer(req, USER_SUB);

        assertThat(result).hasSize(2);
        TransactionDTO toDto   = result.get(0);
        TransactionDTO fromDto = result.get(1);

        assertThat(toDto.amount()).isEqualByComparingTo("100.00");
        assertThat(fromDto.amount()).isEqualByComparingTo("-100.00");
        assertThat(toDto.transferId()).isEqualTo(FROM_TX_ID);
        assertThat(fromDto.transferId()).isEqualTo(TO_TX_ID);

        verify(transactionRepository, times(3)).save(any());
    }

    @Test
    void createTransfer_throwsForbidden_whenFromAccountBelongsToOtherUser() {
        Account otherFromAccount = new Account();
        otherFromAccount.setId(FROM_ACCOUNT_ID);
        otherFromAccount.setUserSub(OTHER_SUB);

        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(otherFromAccount));

        assertThatThrownBy(() -> transferService.createTransfer(req, USER_SUB))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createTransfer_throwsForbidden_whenToAccountBelongsToOtherUser() {
        Account otherToAccount = new Account();
        otherToAccount.setId(TO_ACCOUNT_ID);
        otherToAccount.setUserSub(OTHER_SUB);

        when(accountRepository.findById(TO_ACCOUNT_ID)).thenReturn(Optional.of(otherToAccount));
        when(accountRepository.findById(FROM_ACCOUNT_ID)).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> transferService.createTransfer(req, USER_SUB))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- deleteTransfer ---

    @Test
    void deleteTransfer_deletesBothLegs() {
        Transaction toTx   = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);
        Transaction fromTx = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));
        when(transactionRepository.findById(FROM_TX_ID)).thenReturn(Optional.of(fromTx));

        transferService.deleteTransfer(TO_TX_ID, USER_SUB);

        verify(transactionRepository).delete(toTx);
        verify(transactionRepository).delete(fromTx);
    }

    @Test
    void deleteTransfer_throwsForbidden_whenTransactionBelongsToOtherUser() {
        Transaction toTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), FROM_TX_ID);
        toTx.getAccount().setUserSub(OTHER_SUB);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));

        assertThatThrownBy(() -> transferService.deleteTransfer(TO_TX_ID, USER_SUB))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void deleteTransfer_throwsBadRequest_whenNotATransferLeg() {
        Transaction standaloneTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), null);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(standaloneTx));

        assertThatThrownBy(() -> transferService.deleteTransfer(TO_TX_ID, USER_SUB))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    // --- updateTransfer ---

    @Test
    void updateTransfer_syncsDateAmountMemoCleared() {
        Transaction toTx   = buildTx(TO_TX_ID,   toAccount,   new BigDecimal("100.00"),  FROM_TX_ID);
        Transaction fromTx = buildTx(FROM_TX_ID, fromAccount, new BigDecimal("-100.00"), TO_TX_ID);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));
        when(transactionRepository.findById(FROM_TX_ID)).thenReturn(Optional.of(fromTx));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest updateReq = new TransferRequest(
            FROM_ACCOUNT_ID,
            TO_ACCOUNT_ID,
            LocalDate.of(2026, 3, 10),
            new BigDecimal("200.00"),
            "updated memo",
            true
        );

        List<TransactionDTO> result = transferService.updateTransfer(TO_TX_ID, updateReq, USER_SUB);

        assertThat(result).hasSize(2);
        TransactionDTO toDto   = result.stream().filter(d -> d.amount().compareTo(BigDecimal.ZERO) > 0).findFirst().orElseThrow();
        TransactionDTO fromDto = result.stream().filter(d -> d.amount().compareTo(BigDecimal.ZERO) < 0).findFirst().orElseThrow();

        assertThat(toDto.amount()).isEqualByComparingTo("200.00");
        assertThat(fromDto.amount()).isEqualByComparingTo("-200.00");
        assertThat(toDto.date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(fromDto.date()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(toDto.memo()).isEqualTo("updated memo");
        assertThat(fromDto.memo()).isEqualTo("updated memo");
        assertThat(toDto.cleared()).isTrue();
        assertThat(fromDto.cleared()).isTrue();
    }

    @Test
    void updateTransfer_throwsForbidden_whenTransactionBelongsToOtherUser() {
        Transaction toTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), FROM_TX_ID);
        toTx.getAccount().setUserSub(OTHER_SUB);

        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(toTx));

        assertThatThrownBy(() -> transferService.updateTransfer(TO_TX_ID, req, USER_SUB))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateTransfer_throwsBadRequest_whenNotATransferLeg() {
        Transaction normalTx = buildTx(TO_TX_ID, toAccount, new BigDecimal("100.00"), null);
        when(transactionRepository.findById(TO_TX_ID)).thenReturn(Optional.of(normalTx));

        assertThatThrownBy(() -> transferService.updateTransfer(TO_TX_ID, req, USER_SUB))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- helpers ---

    private Transaction buildTx(UUID id, Account account, BigDecimal amount, UUID transferId) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setAccount(account);
        tx.setAmount(amount);
        tx.setTransferId(transferId);
        tx.setDate(LocalDate.of(2026, 1, 15));
        tx.setPayee("Transfer");
        tx.setCleared(false);
        return tx;
    }
}
