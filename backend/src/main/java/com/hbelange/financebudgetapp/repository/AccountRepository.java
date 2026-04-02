package com.hbelange.financebudgetapp.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.id = :accountId")
    BigDecimal findBalanceById(UUID accountId);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.account.id = :accountId")
    boolean existsTransactionsByAccountId(UUID accountId);
}
