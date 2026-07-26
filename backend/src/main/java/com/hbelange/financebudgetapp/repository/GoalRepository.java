package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Goal findByCategory(BudgetCategory category);
    List<Goal> findByCategory_Group_UserSub(String userSub);
}