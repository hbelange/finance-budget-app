package com.hbelange.financebudgetapp.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
