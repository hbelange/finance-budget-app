package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.entity.BudgetCategory;

@Repository
public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, UUID> {
    List<BudgetCategory> findByGroupIdOrderBySortOrderAsc(UUID groupId);
    Optional<BudgetCategory> findTopByGroupIdOrderBySortOrderDesc(UUID groupId);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.categoryId = :categoryId")
    boolean existsTransactionsByCategoryId(UUID categoryId);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.categoryId IN (SELECT bc.id FROM BudgetCategory bc WHERE bc.groupId = :groupId)")
    boolean existsTransactionsByGroupId(@Param("groupId") UUID groupId);

    @Modifying
    @Query("DELETE FROM BudgetCategory bc WHERE bc.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") UUID groupId);
}
