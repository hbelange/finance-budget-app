package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AllocationRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.dto.BudgetCategoryViewDTO;
import com.hbelange.financebudgetapp.dto.BudgetGroupDTO;
import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@Service
public class BudgetService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public BudgetService(
            CategoryGroupRepository categoryGroupRepository,
            BudgetCategoryRepository budgetCategoryRepository,
            BudgetAllocationRepository budgetAllocationRepository,
            TransactionRepository transactionRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.transactionRepository = transactionRepository;
    }

    public BudgetViewDTO getBudget(String monthParam, String userSub) {
        YearMonth month = parseMonth(monthParam);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        BigDecimal totalNet = transactionRepository.sumNetUpToDate(lastDay, userSub);
        BigDecimal totalAssigned = budgetAllocationRepository.sumAssignedUpToMonth(firstDay, userSub);
        BigDecimal readyToAssign = totalNet.subtract(totalAssigned);

        Map<UUID, BigDecimal> assignedByCategory = budgetAllocationRepository.findByMonthAndUserSub(firstDay, userSub)
            .stream().collect(Collectors.toMap(a -> a.getCategoryId(), a -> a.getAssigned()));

        Map<UUID, BigDecimal> spentByCategory = transactionRepository.findSpentByCategoryForMonth(firstDay, lastDay, userSub)
            .stream().collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));

        List<BudgetGroupDTO> groups = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(userSub).stream()
            .map(g -> {
                List<BudgetCategoryViewDTO> cats = budgetCategoryRepository
                    .findByGroupOrderBySortOrderAsc(g).stream()
                    .map(c -> {
                        BigDecimal assigned = assignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
                        BigDecimal spent = spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
                        BigDecimal available = assigned.add(spent);
                        return new BudgetCategoryViewDTO(c.getId(), c.getName(), assigned, spent, available);
                    }).collect(Collectors.toList());
                return new BudgetGroupDTO(g.getId(), g.getName(), cats);
            }).collect(Collectors.toList());

        return new BudgetViewDTO(readyToAssign, groups);
    }

    @Transactional
    public void upsertAllocation(AllocationRequest req, String userSub) {
        YearMonth month = parseMonth(req.month());
        BudgetCategory category = budgetCategoryRepository.findById(req.categoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to allocate to this category");
        }
        budgetAllocationRepository.upsert(req.categoryId(), month.atDay(1), req.assigned());
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format, expected yyyy-MM");
        }
    }
}
