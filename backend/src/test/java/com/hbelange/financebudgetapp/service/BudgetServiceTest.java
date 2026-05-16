package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AllocationRequest;
import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.entity.BudgetAllocation;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private CategoryGroupRepository categoryGroupRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private BudgetAllocationRepository budgetAllocationRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    private UUID groupId;
    private UUID categoryId;
    private CategoryGroup group;
    private BudgetCategory category;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        group = new CategoryGroup();
        group.setId(groupId);
        group.setName("Housing");
        group.setSortOrder(1);

        category = new BudgetCategory();
        category.setId(categoryId);
        category.setGroupId(groupId);
        category.setName("Rent");
        category.setSortOrder(1);
    }

    @Test
    void getBudget_returnsCorrectReadyToAssign() {
        when(transactionRepository.sumNetUpToDate(any())).thenReturn(new BigDecimal("1000.00"));
        when(budgetAllocationRepository.sumAssignedUpToMonth(any())).thenReturn(new BigDecimal("600.00"));
        when(budgetAllocationRepository.findByMonth(any())).thenReturn(List.of());
        when(transactionRepository.findSpentByCategoryForMonth(any(), any())).thenReturn(List.of());
        when(categoryGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        BudgetViewDTO result = budgetService.getBudget("2026-05");

        assertEquals(new BigDecimal("400.00"), result.readyToAssign());
    }

    @Test
    void getBudget_readyToAssignIsNegative_whenOverAssigned() {
        when(transactionRepository.sumNetUpToDate(any())).thenReturn(new BigDecimal("500.00"));
        when(budgetAllocationRepository.sumAssignedUpToMonth(any())).thenReturn(new BigDecimal("800.00"));
        when(budgetAllocationRepository.findByMonth(any())).thenReturn(List.of());
        when(transactionRepository.findSpentByCategoryForMonth(any(), any())).thenReturn(List.of());
        when(categoryGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        BudgetViewDTO result = budgetService.getBudget("2026-05");

        assertEquals(new BigDecimal("-300.00"), result.readyToAssign());
    }

    @Test
    void getBudget_computesAvailablePerCategory() {
        BudgetAllocation allocation = new BudgetAllocation();
        allocation.setCategoryId(categoryId);
        allocation.setMonth(LocalDate.of(2026, 5, 1));
        allocation.setAssigned(new BigDecimal("500.00"));

        when(transactionRepository.sumNetUpToDate(any())).thenReturn(BigDecimal.ZERO);
        when(budgetAllocationRepository.sumAssignedUpToMonth(any())).thenReturn(BigDecimal.ZERO);
        when(budgetAllocationRepository.findByMonth(any())).thenReturn(List.of(allocation));
        when(transactionRepository.findSpentByCategoryForMonth(any(), any()))
            .thenReturn(List.of(new CategorySpent(categoryId, new BigDecimal("-200.00"))));
        when(categoryGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(group));
        when(budgetCategoryRepository.findByGroupIdOrderBySortOrderAsc(groupId)).thenReturn(List.of(category));

        BudgetViewDTO result = budgetService.getBudget("2026-05");

        var cat = result.groups().get(0).categories().get(0);
        assertEquals(new BigDecimal("500.00"), cat.assigned());
        assertEquals(new BigDecimal("-200.00"), cat.spent());
        assertEquals(new BigDecimal("300.00"), cat.available());
    }

    @Test
    void getBudget_returnsZeroAvailable_whenNoAllocationOrSpending() {
        when(transactionRepository.sumNetUpToDate(any())).thenReturn(BigDecimal.ZERO);
        when(budgetAllocationRepository.sumAssignedUpToMonth(any())).thenReturn(BigDecimal.ZERO);
        when(budgetAllocationRepository.findByMonth(any())).thenReturn(List.of());
        when(transactionRepository.findSpentByCategoryForMonth(any(), any())).thenReturn(List.of());
        when(categoryGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(group));
        when(budgetCategoryRepository.findByGroupIdOrderBySortOrderAsc(groupId)).thenReturn(List.of(category));

        BudgetViewDTO result = budgetService.getBudget("2026-05");

        var cat = result.groups().get(0).categories().get(0);
        assertEquals(BigDecimal.ZERO, cat.assigned());
        assertEquals(BigDecimal.ZERO, cat.spent());
        assertEquals(BigDecimal.ZERO, cat.available());
    }

    @Test
    void getBudget_throwsBadRequest_whenMonthFormatInvalid() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> budgetService.getBudget("not-a-month"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void upsertAllocation_callsRepository() {
        when(budgetCategoryRepository.existsById(categoryId)).thenReturn(true);

        budgetService.upsertAllocation(new AllocationRequest(categoryId, "2026-05", new BigDecimal("300.00")));

        verify(budgetAllocationRepository).upsert(categoryId, LocalDate.of(2026, 5, 1), new BigDecimal("300.00"));
    }

    @Test
    void upsertAllocation_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.existsById(categoryId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> budgetService.upsertAllocation(new AllocationRequest(categoryId, "2026-05", BigDecimal.ZERO)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
