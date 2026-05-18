package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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

import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.dto.DashboardDto;
import com.hbelange.financebudgetapp.dto.SpendingByCategoryDto;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;

    @InjectMocks
    private ReportService reportService;

    private static final String USER_SUB = "auth0|test-user";

    private UUID categoryId;
    private BudgetCategory category;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();

        category = new BudgetCategory();
        category.setId(categoryId);
        category.setName("Rent");
        category.setSortOrder(1);
    }

    @Test
    void getDashboard_returnsCorrectValues() {
        when(transactionRepository.sumNetWorth(USER_SUB)).thenReturn(new BigDecimal("10000.00"));
        when(transactionRepository.sumIncomeForMonth(any(), any(), any())).thenReturn(new BigDecimal("3000.00"));
        when(transactionRepository.sumSpentForMonth(any(), any(), any())).thenReturn(new BigDecimal("-1500.00"));

        DashboardDto result = reportService.getDashboard("2026-05", USER_SUB);

        assertEquals(new BigDecimal("10000.00"), result.netWorth());
        assertEquals(new BigDecimal("3000.00"), result.incomeThisMonth());
        assertEquals(new BigDecimal("1500.00"), result.spentThisMonth());
    }

    @Test
    void getDashboard_spentIsAbsoluteValue() {
        when(transactionRepository.sumNetWorth(USER_SUB)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumIncomeForMonth(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSpentForMonth(any(), any(), any())).thenReturn(new BigDecimal("-500.00"));

        DashboardDto result = reportService.getDashboard("2026-05", USER_SUB);

        assertEquals(new BigDecimal("500.00"), result.spentThisMonth());
    }

    @Test
    void getDashboard_returnsZeros_whenNoTransactions() {
        when(transactionRepository.sumNetWorth(USER_SUB)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumIncomeForMonth(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSpentForMonth(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = reportService.getDashboard("2026-05", USER_SUB);

        assertEquals(BigDecimal.ZERO, result.netWorth());
        assertEquals(BigDecimal.ZERO, result.incomeThisMonth());
        assertEquals(BigDecimal.ZERO, result.spentThisMonth());
    }

    @Test
    void getDashboard_throwsBadRequest_whenMonthInvalid() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.getDashboard("not-a-month", USER_SUB));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSpendingByCategory_returnsSortedBySpentDesc() {
        UUID catId2 = UUID.randomUUID();
        BudgetCategory cat2 = new BudgetCategory();
        cat2.setId(catId2);
        cat2.setName("Groceries");
        cat2.setSortOrder(2);

        when(transactionRepository.findExpenseByCategoryForMonth(any(), any(), any())).thenReturn(List.of(
                new CategorySpent(categoryId, new BigDecimal("-500.00")),
                new CategorySpent(catId2, new BigDecimal("-1200.00"))));
        when(budgetCategoryRepository.findAllById(any())).thenReturn(List.of(category, cat2));

        List<SpendingByCategoryDto> result = reportService.getSpendingByCategory("2026-05", USER_SUB);

        assertEquals(2, result.size());
        assertEquals("Groceries", result.get(0).categoryName());
        assertEquals(new BigDecimal("1200.00"), result.get(0).spent());
        assertEquals("Rent", result.get(1).categoryName());
        assertEquals(new BigDecimal("500.00"), result.get(1).spent());
    }

    @Test
    void getSpendingByCategory_returnsEmpty_whenNoSpending() {
        when(transactionRepository.findExpenseByCategoryForMonth(any(), any(), any())).thenReturn(List.of());

        List<SpendingByCategoryDto> result = reportService.getSpendingByCategory("2026-05", USER_SUB);

        assertTrue(result.isEmpty());
        verifyNoInteractions(budgetCategoryRepository);
    }

    @Test
    void getSpendingByCategory_throwsBadRequest_whenMonthInvalid() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.getSpendingByCategory("bad-month", USER_SUB));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
