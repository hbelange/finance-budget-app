package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Goal findByCategory(BudgetCategory category);
    List<Goal> findByCategory_Group_UserSub(String userSub);

    // A plain derived "deleteBy" here would hydrate full Goal->BudgetCategory->CategoryGroup
    // graphs as a side effect (both associations are EAGER by default), which then go stale the
    // moment a later bulk delete removes those same rows out from under the persistence context.
    // A hand-written bulk delete (matching BudgetAllocationRepository/BudgetCategoryRepository's
    // deleteByGroupUserSub) never hydrates entities at all, avoiding that entirely.
    @Modifying
    @Query("DELETE FROM Goal g WHERE g.category.group.userSub = :userSub")
    void deleteByCategoryGroupUserSub(@Param("userSub") String userSub);
}
