package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountBalance;
import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
        account.setName("Test Account");
        account.setType(AccountType.CHECKING);
    }

    @Test
    void findAll_returnsAccountsWithComputedBalance() {
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findAllBalances()).thenReturn(List.of(new AccountBalance(accountId, new BigDecimal("500.00"))));

        List<AccountDTO> result = accountService.findAll();

        assertEquals(1, result.size());
        AccountDTO dto = result.get(0);
        assertEquals(accountId, dto.id());
        assertEquals("Test Account", dto.name());
        assertEquals(AccountType.CHECKING, dto.type());
        assertEquals(new BigDecimal("500.00"), dto.balance());
    }

    @Test
    void findAll_returnsZeroBalance_whenAccountHasNoTransactions() {
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findAllBalances()).thenReturn(List.of());

        List<AccountDTO> result = accountService.findAll();

        assertEquals(1, result.size());
        assertEquals(BigDecimal.ZERO, result.get(0).balance());
    }

    @Test
    void findAll_returnsEmptyList_whenNoAccounts() {
        when(accountRepository.findAll()).thenReturn(List.of());
        when(accountRepository.findAllBalances()).thenReturn(List.of());

        List<AccountDTO> result = accountService.findAll();

        assertTrue(result.isEmpty());
    }

    @Test
    void create_savesAndReturnsDto() {
        AccountRequest req = new AccountRequest("New Account", AccountType.SAVINGS);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountDTO result = accountService.create(req);

        assertEquals(accountId, result.id());
        assertEquals(BigDecimal.ZERO, result.balance());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void update_updatesAndReturnsDto() {
        AccountRequest req = new AccountRequest("Updated Name", AccountType.SAVINGS);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountRepository.findBalanceById(accountId)).thenReturn(new BigDecimal("100.00"));

        AccountDTO result = accountService.update(accountId, req);

        assertEquals("Updated Name", result.name());
        assertEquals(AccountType.SAVINGS, result.type());
        assertEquals(new BigDecimal("100.00"), result.balance());
    }

    @Test
    void update_throwsNotFound_whenAccountMissing() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.update(accountId, new AccountRequest("X", AccountType.CHECKING)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void delete_deletesAccount_whenNoTransactions() {
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(accountRepository.existsTransactionsByAccountId(accountId)).thenReturn(false);

        accountService.delete(accountId);

        verify(accountRepository).deleteById(accountId);
    }

    @Test
    void delete_throwsConflict_whenTransactionsExist() {
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(accountRepository.existsTransactionsByAccountId(accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.delete(accountId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void delete_throwsNotFound_whenAccountMissing() {
        when(accountRepository.existsById(accountId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.delete(accountId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(accountRepository, never()).deleteById(any());
    }
}
