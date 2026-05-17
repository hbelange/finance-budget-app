package com.hbelange.financebudgetapp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.enums.AccountType;

@DataJpaTest
class BudgetCategoryRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BudgetCategoryRepository budgetCategoryRepository;

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private CategoryGroup group;
    private BudgetCategory cat1;
    private BudgetCategory cat2;
    private Account account;

    @BeforeEach
    void setUp() {
        group = new CategoryGroup();
        group.setName("Housing");
        group.setSortOrder(0);
        group.setUserSub("auth0|test-user");
        group = categoryGroupRepository.save(group);

        cat1 = saveCategory("Rent", 0);
        cat2 = saveCategory("Utilities", 1);

        account = new Account();
        account.setName("Checking");
        account.setType(AccountType.CHECKING);
        account.setUserSub("auth0|test-user");
        account = accountRepository.save(account);
    }

    // --- findByGroupOrderBySortOrderAsc ---

    @Test
    void findByGroupOrderBySortOrderAsc_returnsOrderedCategories() {
        List<BudgetCategory> result = budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Rent");
        assertThat(result.get(1).getName()).isEqualTo("Utilities");
    }

    @Test
    void findByGroupOrderBySortOrderAsc_returnsEmpty_whenGroupHasNoCategories() {
        CategoryGroup empty = new CategoryGroup();
        empty.setName("Empty");
        empty.setSortOrder(1);
        empty.setUserSub("auth0|test-user");
        empty = categoryGroupRepository.save(empty);

        List<BudgetCategory> result = budgetCategoryRepository.findByGroupOrderBySortOrderAsc(empty);
        assertThat(result).isEmpty();
    }

    // --- findTopByGroupOrderBySortOrderDesc ---

    @Test
    void findTopByGroupOrderBySortOrderDesc_returnsHighestSortOrder() {
        Optional<BudgetCategory> result = budgetCategoryRepository.findTopByGroupOrderBySortOrderDesc(group);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Utilities");
        assertThat(result.get().getSortOrder()).isEqualTo(1);
    }

    @Test
    void findTopByGroupOrderBySortOrderDesc_returnsEmpty_whenNoCategories() {
        CategoryGroup empty = new CategoryGroup();
        empty.setName("Empty");
        empty.setSortOrder(1);
        empty.setUserSub("auth0|test-user");
        empty = categoryGroupRepository.save(empty);

        Optional<BudgetCategory> result = budgetCategoryRepository.findTopByGroupOrderBySortOrderDesc(empty);
        assertThat(result).isEmpty();
    }

    // --- existsTransactionsByCategoryId ---

    @Test
    void existsTransactionsByCategoryId_returnsTrue_whenTransactionHasThatCategory() {
        saveTransaction(cat1.getId());
        assertTrue(budgetCategoryRepository.existsTransactionsByCategoryId(cat1.getId()));
    }

    @Test
    void existsTransactionsByCategoryId_returnsFalse_whenNoTransactions() {
        assertFalse(budgetCategoryRepository.existsTransactionsByCategoryId(cat1.getId()));
    }

    @Test
    void existsTransactionsByCategoryId_returnsFalse_forDifferentCategory() {
        saveTransaction(cat1.getId());
        assertFalse(budgetCategoryRepository.existsTransactionsByCategoryId(cat2.getId()));
    }

    // --- existsTransactionsByGroupId ---

    @Test
    void existsTransactionsByGroupId_returnsTrue_whenAnyCategoryInGroupHasTransaction() {
        saveTransaction(cat2.getId());
        assertTrue(budgetCategoryRepository.existsTransactionsByGroupId(group.getId()));
    }

    @Test
    void existsTransactionsByGroupId_returnsFalse_whenNoTransactionsInGroup() {
        assertFalse(budgetCategoryRepository.existsTransactionsByGroupId(group.getId()));
    }

    @Test
    void existsTransactionsByGroupId_returnsFalse_forDifferentGroup() {
        CategoryGroup otherGroup = new CategoryGroup();
        otherGroup.setName("Other");
        otherGroup.setSortOrder(1);
        otherGroup.setUserSub("auth0|test-user");
        otherGroup = categoryGroupRepository.save(otherGroup);

        BudgetCategory otherCat = saveCategory(otherGroup, "Other Cat", 0);
        saveTransaction(otherCat.getId());

        assertFalse(budgetCategoryRepository.existsTransactionsByGroupId(group.getId()));
    }

    // --- deleteByGroupId ---

    @Test
    void deleteByGroupId_removesAllCategoriesInGroup() {
        budgetCategoryRepository.deleteByGroupId(group.getId());
        em.flush();
        em.clear();

        List<BudgetCategory> remaining = budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group);
        assertThat(remaining).isEmpty();
    }

    @Test
    void deleteByGroupId_doesNotAffectOtherGroups() {
        CategoryGroup otherGroup = new CategoryGroup();
        otherGroup.setName("Other");
        otherGroup.setSortOrder(1);
        otherGroup.setUserSub("auth0|test-user");
        otherGroup = categoryGroupRepository.save(otherGroup);
        BudgetCategory otherCat = saveCategory(otherGroup, "Other Cat", 0);

        budgetCategoryRepository.deleteByGroupId(group.getId());
        em.flush();
        em.clear();

        assertThat(budgetCategoryRepository.findById(otherCat.getId())).isPresent();
    }

    private BudgetCategory saveCategory(String name, int sortOrder) {
        return saveCategory(group, name, sortOrder);
    }

    private BudgetCategory saveCategory(CategoryGroup g, String name, int sortOrder) {
        BudgetCategory cat = new BudgetCategory();
        cat.setGroup(g);
        cat.setName(name);
        cat.setSortOrder(sortOrder);
        return budgetCategoryRepository.save(cat);
    }

    private void saveTransaction(UUID categoryId) {
        Transaction t = new Transaction();
        t.setAccount(account);
        t.setDate(LocalDate.now());
        t.setAmount(new BigDecimal("-50.00"));
        t.setCategoryId(categoryId);
        transactionRepository.save(t);
    }
}
