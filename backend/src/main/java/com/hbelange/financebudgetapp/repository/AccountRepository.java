package com.hbelange.financebudgetapp.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hbelange.financebudgetapp.dto.AccountBalance;
import com.hbelange.financebudgetapp.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account.id = :accountId")
    BigDecimal findBalanceById(UUID accountId);

    /** Returns the balance for every account that has at least one transaction. */
    @Query("SELECT NEW com.hbelange.financebudgetapp.dto.AccountBalance(t.account.id, COALESCE(SUM(t.amount), 0)) FROM Transaction t GROUP BY t.account.id")
    List<AccountBalance> findAllBalances();

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.account.id = :accountId")
    boolean existsTransactionsByAccountId(UUID accountId);

    List<Account> findByUserSub(String userSub);

    @Query("SELECT NEW com.hbelange.financebudgetapp.dto.AccountBalance(t.account.id, COALESCE(SUM(t.amount), 0)) FROM Transaction t WHERE t.account.userSub = :userSub GROUP BY t.account.id")
    List<AccountBalance> findBalancesByUserSub(String userSub);
}
