package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.enums.AccountType;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock private CategoryGroupRepository categoryGroupRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private CreditCardService creditCardService;

    private static final String USER_SUB = "auth0|test-user";

    private Account ccAccount;
    private CategoryGroup ccGroup;
    private BudgetCategory ccCategory;

    @BeforeEach
    void setUp() {
        ccAccount = new Account();
        ccAccount.setId(UUID.randomUUID());
        ccAccount.setName("My Visa");
        ccAccount.setType(AccountType.CREDIT_CARD);
        ccAccount.setUserSub(USER_SUB);

        ccGroup = new CategoryGroup();
        ccGroup.setId(UUID.randomUUID());
        ccGroup.setName("Credit Card Payments");
        ccGroup.setSortOrder(5);
        ccGroup.setUserSub(USER_SUB);

        ccCategory = new BudgetCategory();
        ccCategory.setId(UUID.randomUUID());
        ccCategory.setGroup(ccGroup);
        ccCategory.setName("My Visa");
        ccCategory.setSortOrder(0);
    }

    // --- ensureCCPaymentCategory ---

    @Test
    void ensureCCPaymentCategory_createsGroupAndCategory_whenGroupDoesNotExist() {
        when(categoryGroupRepository.findByUserSubAndName(USER_SUB, "Credit Card Payments"))
            .thenReturn(Optional.empty());
        when(categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_SUB))
            .thenReturn(Optional.of(ccGroup));
        when(categoryGroupRepository.save(any(CategoryGroup.class))).thenReturn(ccGroup);
        when(budgetCategoryRepository.save(any(BudgetCategory.class))).thenReturn(ccCategory);
        when(accountRepository.save(any(Account.class))).thenReturn(ccAccount);

        creditCardService.ensureCCPaymentCategory(ccAccount, USER_SUB);

        verify(categoryGroupRepository).save(argThat(g ->
            g.getName().equals("Credit Card Payments") && g.getUserSub().equals(USER_SUB)));
        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
        verify(accountRepository).save(argThat(a -> a.getCcPaymentCategoryId() != null));
    }

    @Test
    void ensureCCPaymentCategory_reusesExistingGroup() {
        when(categoryGroupRepository.findByUserSubAndName(USER_SUB, "Credit Card Payments"))
            .thenReturn(Optional.of(ccGroup));
        when(budgetCategoryRepository.save(any(BudgetCategory.class))).thenReturn(ccCategory);
        when(accountRepository.save(any(Account.class))).thenReturn(ccAccount);

        creditCardService.ensureCCPaymentCategory(ccAccount, USER_SUB);

        verify(categoryGroupRepository, never()).save(any(CategoryGroup.class));
        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
    }

    // --- syncCategoryName ---

    @Test
    void syncCategoryName_renamesLinkedCategory() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));

        creditCardService.syncCategoryName(ccAccount);

        verify(budgetCategoryRepository).save(argThat(c -> c.getName().equals("My Visa")));
    }

    // --- deleteCCPaymentCategory ---

    @Test
    void deleteCCPaymentCategory_deletesCategoryAndClearsField() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of());

        creditCardService.deleteCCPaymentCategory(ccAccount);

        assertNull(ccAccount.getCcPaymentCategoryId());
        verify(budgetCategoryRepository).deleteById(ccCategory.getId());
    }

    @Test
    void deleteCCPaymentCategory_deletesGroupWhenNoOtherCCCategories() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of());

        creditCardService.deleteCCPaymentCategory(ccAccount);

        verify(categoryGroupRepository).deleteById(ccGroup.getId());
    }

    @Test
    void deleteCCPaymentCategory_keepsGroupWhenOtherCCCategoriesRemain() {
        ccAccount.setCcPaymentCategoryId(ccCategory.getId());
        BudgetCategory other = new BudgetCategory();
        other.setId(UUID.randomUUID());
        other.setGroup(ccGroup);
        other.setName("Other Card");
        other.setSortOrder(1);

        when(budgetCategoryRepository.findById(ccCategory.getId())).thenReturn(Optional.of(ccCategory));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(ccGroup)).thenReturn(List.of(other));

        creditCardService.deleteCCPaymentCategory(ccAccount);

        verify(categoryGroupRepository, never()).deleteById(any());
    }

    @Test
    void deleteCCPaymentCategory_doesNothingWhenNoCcPaymentCategoryId() {
        // ccAccount has null ccPaymentCategoryId (default)
        creditCardService.deleteCCPaymentCategory(ccAccount);

        verifyNoInteractions(budgetCategoryRepository);
        verifyNoInteractions(categoryGroupRepository);
    }
}
