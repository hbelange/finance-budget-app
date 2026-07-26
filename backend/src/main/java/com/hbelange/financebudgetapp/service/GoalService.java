package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.GoalDTO;
import com.hbelange.financebudgetapp.dto.GoalRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.GoalRepository;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    public GoalService(GoalRepository goalRepository, BudgetCategoryRepository budgetCategoryRepository) {
        this.goalRepository = goalRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
    }

    public GoalDTO createGoal(GoalRequest goalRequest, String userSub) {
        BudgetCategory category = budgetCategoryRepository.findById(goalRequest.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to add a goal to this category");
        }

        Goal goal = new Goal();
        goal.setCategory(category);
        goal.setAmount(goalRequest.amount());
        goal.setDayOfMonth(goalRequest.dayOfMonth());
        goal.setRolloverType(goalRequest.rolloverType());
        goal = goalRepository.save(goal);

        return toDto(goal);
    }

    public GoalDTO getGoalByCategoryId(UUID categoryId, String userSub) {
        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        return toDto(goal);
    }

    public GoalDTO updateGoal(UUID categoryId, GoalRequest goalRequest, String userSub) {

        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to update the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        goal.setAmount(goalRequest.amount());
        goal.setDayOfMonth(goalRequest.dayOfMonth());
        goal.setRolloverType(goalRequest.rolloverType());
        goal = goalRepository.save(goal);

        return toDto(goal);
    }

    public void deleteGoal(UUID categoryId, String userSub) {

        BudgetCategory category = budgetCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getGroup().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete the goal for this category");
        }

        Goal goal = goalRepository.findByCategory(category);

        if (goal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No goal found for this category");
        }

        goalRepository.delete(goal);
    }

    public List<GoalDTO> getAllGoals(String userSub) {
        return goalRepository.findByCategory_Group_UserSub(userSub).stream().map(this::toDto).toList();
    }

    private GoalDTO toDto(Goal goal) {
        return new GoalDTO(goal.getId(), goal.getCategory().getId(), goal.getAmount(), goal.getDayOfMonth(), goal.getRolloverType());
    }
}
