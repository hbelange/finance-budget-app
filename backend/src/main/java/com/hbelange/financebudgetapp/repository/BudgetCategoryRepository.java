package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.entity.BudgetCategory;

@Repository
public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, UUID> {
    List<BudgetCategory> findByGroupIdOrderBySortOrderAsc(UUID groupId);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.categoryId = :categoryId")
    boolean existsTransactionsByCategoryId(UUID categoryId);
}
