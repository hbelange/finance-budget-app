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
import com.hbelange.financebudgetapp.service.CreditCardService;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CreditCardService creditCardService;

    @InjectMocks
    private AccountService accountService;

    private static final String USER_SUB = "auth0|test-user";
    private static final String OTHER_SUB = "auth0|other-user";

    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
        account.setName("Test Account");
        account.setType(AccountType.CHECKING);
        account.setUserSub(USER_SUB);
    }

    @Test
    void findAll_returnsAccountsWithComputedBalance() {
        when(accountRepository.findByUserSub(USER_SUB)).thenReturn(List.of(account));
        when(accountRepository.findBalancesByUserSub(USER_SUB)).thenReturn(List.of(new AccountBalance(accountId, new BigDecimal("500.00"))));

        List<AccountDTO> result = accountService.findAll(USER_SUB);

        assertEquals(1, result.size());
        AccountDTO dto = result.get(0);
        assertEquals(accountId, dto.id());
        assertEquals("Test Account", dto.name());
        assertEquals(AccountType.CHECKING, dto.type());
        assertEquals(new BigDecimal("500.00"), dto.balance());
    }

    @Test
    void findAll_returnsZeroBalance_whenAccountHasNoTransactions() {
        when(accountRepository.findByUserSub(USER_SUB)).thenReturn(List.of(account));
        when(accountRepository.findBalancesByUserSub(USER_SUB)).thenReturn(List.of());

        List<AccountDTO> result = accountService.findAll(USER_SUB);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.ZERO, result.get(0).balance());
    }

    @Test
    void findAll_returnsEmptyList_whenNoAccounts() {
        when(accountRepository.findByUserSub(USER_SUB)).thenReturn(List.of());
        when(accountRepository.findBalancesByUserSub(USER_SUB)).thenReturn(List.of());

        List<AccountDTO> result = accountService.findAll(USER_SUB);

        assertTrue(result.isEmpty());
    }

    @Test
    void create_savesAndReturnsDto() {
        AccountRequest req = new AccountRequest("New Account", AccountType.SAVINGS);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountDTO result = accountService.create(req, USER_SUB);

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

        AccountDTO result = accountService.update(accountId, req, USER_SUB);

        assertEquals("Updated Name", result.name());
        assertEquals(AccountType.SAVINGS, result.type());
        assertEquals(new BigDecimal("100.00"), result.balance());
    }

    @Test
    void update_throwsNotFound_whenAccountMissing() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.update(accountId, new AccountRequest("X", AccountType.CHECKING), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void update_throwsForbidden_whenAccountBelongsToOtherUser() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.update(accountId, new AccountRequest("X", AccountType.CHECKING), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void delete_deletesAccount_whenNoTransactions() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.existsTransactionsByAccountId(accountId)).thenReturn(false);

        accountService.delete(accountId, USER_SUB);

        verify(accountRepository).deleteById(accountId);
    }

    @Test
    void delete_throwsConflict_whenTransactionsExist() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.existsTransactionsByAccountId(accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.delete(accountId, USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void delete_throwsNotFound_whenAccountMissing() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.delete(accountId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void delete_throwsForbidden_whenAccountBelongsToOtherUser() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> accountService.delete(accountId, OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void create_callsEnsureCCPaymentCategory_whenCreditCard() {
        Account saved = new Account();
        saved.setId(accountId);
        saved.setName("My Visa");
        saved.setType(AccountType.CREDIT_CARD);
        saved.setUserSub(USER_SUB);

        AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);

        accountService.create(req, USER_SUB);

        verify(creditCardService).ensureCCPaymentCategory(saved, USER_SUB);
    }

    @Test
    void create_doesNotCallCCService_whenNotCreditCard() {
        AccountRequest req = new AccountRequest("Checking", AccountType.CHECKING);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        accountService.create(req, USER_SUB);

        verify(creditCardService, never()).ensureCCPaymentCategory(any(), any());
    }

    @Test
    void update_callsSyncCategoryName_whenNameChangedOnCC() {
        account.setType(AccountType.CREDIT_CARD);
        AccountRequest req = new AccountRequest("New Name", AccountType.CREDIT_CARD);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

        accountService.update(accountId, req, USER_SUB);

        verify(creditCardService).syncCategoryName(any(Account.class));
    }

    @Test
    void update_doesNotCallSyncCategoryName_whenNameUnchanged() {
        account.setType(AccountType.CREDIT_CARD);
        account.setName("My Visa");
        AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

        accountService.update(accountId, req, USER_SUB);

        verify(creditCardService, never()).syncCategoryName(any());
    }

    @Test
    void update_callsEnsureCCPaymentCategory_whenTypeChangedToCC() {
        account.setType(AccountType.CHECKING);
        AccountRequest req = new AccountRequest("My Visa", AccountType.CREDIT_CARD);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

        accountService.update(accountId, req, USER_SUB);

        verify(creditCardService).ensureCCPaymentCategory(any(Account.class), eq(USER_SUB));
        verify(creditCardService, never()).deleteCCPaymentCategory(any());
    }

    @Test
    void update_callsDeleteCCPaymentCategory_whenTypeChangedAwayFromCC() {
        account.setType(AccountType.CREDIT_CARD);
        AccountRequest req = new AccountRequest("My Account", AccountType.CHECKING);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountRepository.findBalanceById(accountId)).thenReturn(BigDecimal.ZERO);

        accountService.update(accountId, req, USER_SUB);

        verify(creditCardService).deleteCCPaymentCategory(account);
        verify(creditCardService, never()).ensureCCPaymentCategory(any(), any());
    }
}
