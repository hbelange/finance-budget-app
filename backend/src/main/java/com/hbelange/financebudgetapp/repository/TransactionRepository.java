package com.hbelange.financebudgetapp.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.dto.CategorySpent;
import com.hbelange.financebudgetapp.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByAccountId(UUID accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.date BETWEEN :start AND :end")
    Page<Transaction> findByDateBetween(LocalDate start, LocalDate end, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.date BETWEEN :start AND :end")
    Page<Transaction> findByAccountIdBetween(UUID accountId, LocalDate start, LocalDate end, Pageable pageable);

    default Page<Transaction> findByMonth(YearMonth month, Pageable pageable) {
        return findByDateBetween(month.atDay(1), month.atEndOfMonth(), pageable);
    }

    default Page<Transaction> findByAccountIdAndMonth(UUID accountId, YearMonth month, Pageable pageable) {
        return findByAccountIdBetween(accountId, month.atDay(1), month.atEndOfMonth(), pageable);
    }

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.date <= :lastDayOfMonth AND t.account.userSub = :userSub")
    BigDecimal sumNetUpToDate(@Param("lastDayOfMonth") LocalDate lastDayOfMonth, @Param("userSub") String userSub);

    @Query("SELECT NEW com.hbelange.financebudgetapp.dto.CategorySpent(t.categoryId, COALESCE(SUM(t.amount), 0)) FROM Transaction t WHERE t.categoryId IS NOT NULL AND t.date BETWEEN :start AND :end AND t.account.userSub = :userSub GROUP BY t.categoryId")
    List<CategorySpent> findSpentByCategoryForMonth(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("userSub") String userSub);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.userSub = :userSub")
    BigDecimal sumNetWorth(@Param("userSub") String userSub);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transferId IS NULL AND t.amount > 0 AND t.date BETWEEN :start AND :end AND t.account.userSub = :userSub")
    BigDecimal sumIncomeForMonth(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("userSub") String userSub);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transferId IS NULL AND t.amount < 0 AND t.date BETWEEN :start AND :end AND t.account.userSub = :userSub")
    BigDecimal sumSpentForMonth(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("userSub") String userSub);

    @Query("SELECT NEW com.hbelange.financebudgetapp.dto.CategorySpent(t.categoryId, COALESCE(SUM(t.amount), 0)) FROM Transaction t WHERE t.categoryId IS NOT NULL AND t.amount < 0 AND t.date BETWEEN :start AND :end AND t.account.userSub = :userSub GROUP BY t.categoryId")
    List<CategorySpent> findExpenseByCategoryForMonth(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("userSub") String userSub);

    Page<Transaction> findByAccountUserSub(String userSub, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.userSub = :userSub AND t.date BETWEEN :start AND :end")
    Page<Transaction> findByUserSubAndDateBetween(@Param("userSub") String userSub, @Param("start") LocalDate start, @Param("end") LocalDate end, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.date <= :lastDay AND t.account.userSub = :userSub " +
           "AND NOT (t.account.type = 'CREDIT_CARD' AND t.categoryId IS NOT NULL AND t.transferId IS NULL)")
    BigDecimal sumNetExcludingCCPurchases(@Param("lastDay") LocalDate lastDay, @Param("userSub") String userSub);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.id = :accountId AND t.date <= :lastDay")
    BigDecimal sumForAccount(@Param("accountId") UUID accountId, @Param("lastDay") LocalDate lastDay);

    /** Returns null when the table is empty — expected behavior for an aggregate with no rows. */
    @Query("SELECT MIN(t.date) FROM Transaction t")
    LocalDate findMinDate();

    /** Returns null when the table is empty — expected behavior for an aggregate with no rows. */
    @Query("SELECT MAX(t.date) FROM Transaction t")
    LocalDate findMaxDate();
}
