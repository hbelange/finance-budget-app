package com.hbelange.financebudgetapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.hbelange.financebudgetapp.entity.BudgetAllocation;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;

@DataJpaTest
class BudgetAllocationRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BudgetAllocationRepository budgetAllocationRepository;

    @Autowired
    private BudgetCategoryRepository budgetCategoryRepository;

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

    private static final String USER_A = "auth0|user-a";
    private static final String USER_B = "auth0|user-b";
    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEB = LocalDate.of(2026, 2, 1);

    private BudgetCategory catA;
    private BudgetCategory catB;

    @BeforeEach
    void setUp() {
        CategoryGroup groupA = saveGroup("Housing", USER_A);
        CategoryGroup groupB = saveGroup("Food", USER_B);

        catA = saveCategory(groupA, "Rent");
        catB = saveCategory(groupB, "Groceries");

        saveAllocation(catA.getId(), JAN, "1000.00");
        saveAllocation(catA.getId(), FEB, "1200.00");
        saveAllocation(catB.getId(), JAN, "500.00");
    }

    // --- findByMonthAndUserSub ---

    @Test
    void findByMonthAndUserSub_returnsAllocationsForThatMonthAndUser() {
        List<BudgetAllocation> result = budgetAllocationRepository.findByMonthAndUserSub(JAN, USER_A);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAssigned()).isEqualByComparingTo("1000.00");
    }

    @Test
    void findByMonthAndUserSub_excludesOtherUsersAllocations() {
        List<BudgetAllocation> result = budgetAllocationRepository.findByMonthAndUserSub(JAN, USER_A);
        assertThat(result).noneMatch(a -> a.getCategoryId().equals(catB.getId()));
    }

    @Test
    void findByMonthAndUserSub_returnsEmpty_forUnknownUser() {
        List<BudgetAllocation> result = budgetAllocationRepository.findByMonthAndUserSub(JAN, "auth0|unknown");
        assertThat(result).isEmpty();
    }

    // --- sumAssignedUpToMonth ---

    @Test
    void sumAssignedUpToMonth_returnsCumulativeTotal() {
        BigDecimal result = budgetAllocationRepository.sumAssignedUpToMonth(FEB, USER_A);
        assertThat(result).isEqualByComparingTo("2200.00"); // 1000 + 1200
    }

    @Test
    void sumAssignedUpToMonth_includesOnlyMonthsUpToAndIncluding() {
        BigDecimal result = budgetAllocationRepository.sumAssignedUpToMonth(JAN, USER_A);
        assertThat(result).isEqualByComparingTo("1000.00");
    }

    @Test
    void sumAssignedUpToMonth_excludesOtherUsersAllocations() {
        BigDecimal result = budgetAllocationRepository.sumAssignedUpToMonth(FEB, USER_A);
        assertThat(result).isEqualByComparingTo("2200.00"); // does not include USER_B's 500
    }

    @Test
    void sumAssignedUpToMonth_returnsZero_whenNoAllocations() {
        BigDecimal result = budgetAllocationRepository.sumAssignedUpToMonth(JAN, "auth0|unknown");
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- deleteByGroupId ---

    @Test
    void deleteByGroupId_removesAllocationsForCategoriesInGroup() {
        CategoryGroup groupA = categoryGroupRepository.findAll().stream()
            .filter(g -> g.getUserSub().equals(USER_A))
            .findFirst().orElseThrow();

        budgetAllocationRepository.deleteByGroupId(groupA.getId());
        em.flush();
        em.clear();

        List<BudgetAllocation> remaining = budgetAllocationRepository.findAll();
        assertThat(remaining).noneMatch(a -> a.getCategoryId().equals(catA.getId()));
    }

    @Test
    void deleteByGroupId_doesNotAffectOtherGroupsAllocations() {
        CategoryGroup groupA = categoryGroupRepository.findAll().stream()
            .filter(g -> g.getUserSub().equals(USER_A))
            .findFirst().orElseThrow();

        budgetAllocationRepository.deleteByGroupId(groupA.getId());
        em.flush();
        em.clear();

        List<BudgetAllocation> remaining = budgetAllocationRepository.findAll();
        assertThat(remaining).anyMatch(a -> a.getCategoryId().equals(catB.getId()));
    }

    // --- deleteByCategoryId ---

    @Test
    void deleteByCategoryId_removesAllocationsForThatCategory() {
        budgetAllocationRepository.deleteByCategoryId(catA.getId());
        em.flush();
        em.clear();

        List<BudgetAllocation> remaining = budgetAllocationRepository.findAll();
        assertThat(remaining).noneMatch(a -> a.getCategoryId().equals(catA.getId()));
        assertThat(remaining).anyMatch(a -> a.getCategoryId().equals(catB.getId()));
    }

    private CategoryGroup saveGroup(String name, String userSub) {
        CategoryGroup g = new CategoryGroup();
        g.setName(name);
        g.setSortOrder(0);
        g.setUserSub(userSub);
        return categoryGroupRepository.save(g);
    }

    private BudgetCategory saveCategory(CategoryGroup group, String name) {
        BudgetCategory cat = new BudgetCategory();
        cat.setGroup(group);
        cat.setName(name);
        cat.setSortOrder(0);
        return budgetCategoryRepository.save(cat);
    }

    private void saveAllocation(java.util.UUID categoryId, LocalDate month, String assigned) {
        BudgetAllocation a = new BudgetAllocation();
        a.setCategoryId(categoryId);
        a.setMonth(month);
        a.setAssigned(new BigDecimal(assigned));
        budgetAllocationRepository.save(a);
    }
}
