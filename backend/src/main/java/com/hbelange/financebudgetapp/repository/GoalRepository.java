package com.hbelange.financebudgetapp.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Goal findByCategory(BudgetCategory category);
}