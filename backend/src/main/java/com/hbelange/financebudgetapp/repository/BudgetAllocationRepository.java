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

    List<BudgetAllocation> findByMonth(LocalDate month);

    @Query("SELECT COALESCE(SUM(ba.assigned), 0) FROM BudgetAllocation ba WHERE ba.month <= :firstDayOfMonth")
    BigDecimal sumAssignedUpToMonth(@Param("firstDayOfMonth") LocalDate firstDayOfMonth);

    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO budget_allocations (category_id, month, assigned)
        VALUES (:categoryId, :month, :assigned)
        ON CONFLICT (category_id, month) DO UPDATE SET assigned = EXCLUDED.assigned
        """)
    void upsert(@Param("categoryId") UUID categoryId, @Param("month") LocalDate month, @Param("assigned") BigDecimal assigned);

    @Modifying
    void deleteByCategoryId(UUID categoryId);

    @Modifying
    @Query("DELETE FROM BudgetAllocation ba WHERE ba.categoryId IN (SELECT bc.id FROM BudgetCategory bc WHERE bc.groupId = :groupId)")
    void deleteByGroupId(@Param("groupId") UUID groupId);
}
