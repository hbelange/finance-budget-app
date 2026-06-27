package com.hbelange.financebudgetapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.entity.BudgetAllocation;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, UUID> {

    @Query("SELECT ba FROM BudgetAllocation ba WHERE ba.month = :month AND ba.categoryId IN (SELECT bc.id FROM BudgetCategory bc WHERE bc.group.userSub = :userSub)")
    List<BudgetAllocation> findByMonthAndUserSub(@Param("month") LocalDate month, @Param("userSub") String userSub);

    @Query("SELECT COALESCE(SUM(ba.assigned), 0) FROM BudgetAllocation ba WHERE ba.categoryId IN (SELECT bc.id FROM BudgetCategory bc WHERE bc.group.userSub = :userSub)")
    BigDecimal sumAllAssigned(@Param("userSub") String userSub);

    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO budget_allocations (category_id, "month", assigned)
        VALUES (:categoryId, :month, :assigned)
        ON CONFLICT (category_id, "month") DO UPDATE SET assigned = EXCLUDED.assigned
        """)
    void upsert(@Param("categoryId") UUID categoryId, @Param("month") LocalDate month, @Param("assigned") BigDecimal assigned);

    @Modifying
    void deleteByCategoryId(UUID categoryId);

    @Modifying
    @Query("DELETE FROM BudgetAllocation ba WHERE ba.categoryId IN (SELECT bc.id FROM BudgetCategory bc WHERE bc.group.id = :groupId)")
    void deleteByGroupId(@Param("groupId") UUID groupId);
}
