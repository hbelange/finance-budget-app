package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.dto.DashboardDto;
import com.hbelange.financebudgetapp.dto.SpendingByCategoryDto;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    @Autowired
    public ReportService(TransactionRepository transactionRepository,
                         BudgetCategoryRepository budgetCategoryRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
    }

    public DashboardDto getDashboard(String monthParam, String userSub) {
        YearMonth month = parseMonth(monthParam);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        BigDecimal netWorth = transactionRepository.sumNetWorth(userSub);
        BigDecimal income = transactionRepository.sumIncomeForMonth(firstDay, lastDay, userSub);
        BigDecimal spent = transactionRepository.sumSpentForMonth(firstDay, lastDay, userSub).abs();

        return new DashboardDto(netWorth, income, spent);
    }

    public List<SpendingByCategoryDto> getSpendingByCategory(String monthParam, String userSub) {
        YearMonth month = parseMonth(monthParam);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        List<CategorySpent> expenses = transactionRepository.findExpenseByCategoryForMonth(firstDay, lastDay, userSub);
        if (expenses.isEmpty()) {
            return List.of();
        }

        Set<java.util.UUID> ids = expenses.stream()
                .map(CategorySpent::categoryId)
                .collect(Collectors.toSet());

        Map<java.util.UUID, String> nameById = budgetCategoryRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        return expenses.stream()
                .map(s -> new SpendingByCategoryDto(
                        s.categoryId(),
                        nameById.getOrDefault(s.categoryId(), "Unknown"),
                        s.spent().abs()))
                .sorted(Comparator.comparing(SpendingByCategoryDto::spent).reversed())
                .collect(Collectors.toList());
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format, expected yyyy-MM");
        }
    }
}
