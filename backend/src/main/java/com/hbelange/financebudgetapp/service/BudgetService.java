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
import com.hbelange.financebudgetapp.entity.Goal;
import com.hbelange.financebudgetapp.enums.RolloverType;
import com.hbelange.financebudgetapp.dto.BudgetCategoryViewDTO;
import com.hbelange.financebudgetapp.dto.BudgetGroupDTO;
import com.hbelange.financebudgetapp.dto.BudgetViewDTO;
import com.hbelange.financebudgetapp.dto.CategoryAssigned;
import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;
import com.hbelange.financebudgetapp.repository.GoalRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@Service
public class BudgetService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final GoalRepository goalRepository;

    public BudgetService(
            CategoryGroupRepository categoryGroupRepository,
            BudgetCategoryRepository budgetCategoryRepository,
            BudgetAllocationRepository budgetAllocationRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            GoalRepository goalRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.goalRepository = goalRepository;
    }

    public BudgetViewDTO getBudget(String monthParam, String userSub) {
        YearMonth month = parseMonth(monthParam);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        // RTA = total income ever received (positive, non-transfer) minus total ever assigned.
        // Global — no date cutoff — so it stays constant when switching months.
        BigDecimal totalIncome = transactionRepository.sumRtaBase(userSub);
        BigDecimal totalAssigned = budgetAllocationRepository.sumAllAssigned(userSub);
        BigDecimal readyToAssign = totalIncome.subtract(totalAssigned);

        Map<UUID, UUID> ccPaymentCategoryToAccount = accountRepository
            .findByUserSubAndCcPaymentCategoryIdNotNull(userSub)
            .stream()
            .collect(Collectors.toMap(a -> a.getCcPaymentCategoryId(), a -> a.getId()));

        Map<UUID, BigDecimal> assignedByCategory = budgetAllocationRepository.findByMonthAndUserSub(firstDay, userSub)
            .stream().collect(Collectors.toMap(a -> a.getCategoryId(), a -> a.getAssigned()));

        Map<UUID, BigDecimal> spentByCategory = transactionRepository.findSpentByCategoryForMonth(firstDay, lastDay, userSub)
            .stream().collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));

        // "Available" carries a category's leftover balance forward, so it's the running total of
        // assigned + spent across all months up to and including M — not just this month's activity.
        Map<UUID, BigDecimal> cumulativeAssignedByCategory = budgetAllocationRepository.sumAssignedByCategoryThroughMonth(lastDay, userSub)
            .stream().collect(Collectors.toMap(CategoryAssigned::categoryId, CategoryAssigned::assigned));

        Map<UUID, BigDecimal> cumulativeSpentByCategory = transactionRepository.sumSpentByCategoryThroughMonth(lastDay, userSub)
            .stream().collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));

        List<BudgetGroupDTO> groups = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(userSub).stream()
            .map(g -> {
                List<BudgetCategoryViewDTO> cats = budgetCategoryRepository
                    .findByGroupOrderBySortOrderAsc(g).stream()
                    .map(c -> {
                        // Get category amountNeeded, based on category goal
                        // If ACCUMULATE, amountNeeded = goal - assigned
                        // If REFILL, amountNeeded = goal - available
                        Goal goal = goalRepository.findByCategory(c);
                        BigDecimal amountNeeded = BigDecimal.ZERO;

                        if (goal != null) {
                            if (goal.getRolloverType() == RolloverType.ACCUMULATE) {
                                BigDecimal assigned = assignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
                                amountNeeded = goal.getAmount().subtract(assigned).max(BigDecimal.ZERO);
                            } else if (goal.getRolloverType() == RolloverType.REFILL) {
                                BigDecimal available = cumulativeAssignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO)
                                        .add(cumulativeSpentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO));
                                amountNeeded = goal.getAmount().subtract(available).max(BigDecimal.ZERO);
                            }
                        }

                        return buildCategoryView(c, ccPaymentCategoryToAccount, assignedByCategory, spentByCategory,
                        cumulativeAssignedByCategory, cumulativeSpentByCategory, lastDay, amountNeeded);
                    })
                    .collect(Collectors.toList());
                return new BudgetGroupDTO(g.getId(), g.getName(), cats);
            }).collect(Collectors.toList());

        return new BudgetViewDTO(readyToAssign, groups);
    }

    private BudgetCategoryViewDTO buildCategoryView(BudgetCategory c,
            Map<UUID, UUID> ccPaymentCategoryToAccount,
            Map<UUID, BigDecimal> assignedByCategory,
            Map<UUID, BigDecimal> spentByCategory,
            Map<UUID, BigDecimal> cumulativeAssignedByCategory,
            Map<UUID, BigDecimal> cumulativeSpentByCategory,
            LocalDate lastDay,
            BigDecimal amountNeeded) {
        if (ccPaymentCategoryToAccount.containsKey(c.getId())) {
            UUID accountId = ccPaymentCategoryToAccount.get(c.getId());
            BigDecimal balance = transactionRepository.sumForAccount(accountId, lastDay);
            BigDecimal owed = balance.negate().max(BigDecimal.ZERO);
            return new BudgetCategoryViewDTO(c.getId(), c.getName(), BigDecimal.ZERO, BigDecimal.ZERO, owed, true, BigDecimal.ZERO);
        }
        BigDecimal assigned = assignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal spent = spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal cumulativeAssigned = cumulativeAssignedByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal cumulativeSpent = cumulativeSpentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO);
        BigDecimal available = cumulativeAssigned.add(cumulativeSpent);
        return new BudgetCategoryViewDTO(c.getId(), c.getName(), assigned, spent, available, false, amountNeeded);
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
