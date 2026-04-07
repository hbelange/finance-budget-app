package com.hbelange.financebudgetapp.service;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransactionRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    public Page<TransactionDTO> findAll(UUID accountId, YearMonth month, Pageable pageable) {
        if (accountId != null && month != null) {
            return transactionRepository.findByAccountIdAndMonth(accountId, month, pageable).map(this::toDTO);
        }
        if (accountId != null) {
            return transactionRepository.findByAccountId(accountId, pageable).map(this::toDTO);
        }
        if (month != null) {
            return transactionRepository.findByMonth(month, pageable).map(this::toDTO);
        }
        return transactionRepository.findAll(pageable).map(this::toDTO);
    }

    public TransactionDTO create(TransactionRequest req) {
        Account account = accountRepository.findById(req.accountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + req.accountId()));

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setDate(req.date());
        transaction.setPayee(req.payee());
        transaction.setCategoryId(req.categoryId());
        transaction.setAmount(req.amount());
        transaction.setMemo(req.memo());
        transaction.setCleared(req.cleared());

        return toDTO(transactionRepository.save(transaction));
    }

    public TransactionDTO update(UUID id, TransactionRequest req) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));

        if (!transaction.getAccount().getId().equals(req.accountId())) {
            Account account = accountRepository.findById(req.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + req.accountId()));
            transaction.setAccount(account);
        }
        transaction.setDate(req.date());
        transaction.setPayee(req.payee());
        transaction.setCategoryId(req.categoryId());
        transaction.setAmount(req.amount());
        transaction.setMemo(req.memo());
        transaction.setCleared(req.cleared());

        return toDTO(transactionRepository.save(transaction));
    }

    public void delete(UUID id) {
        if (!transactionRepository.existsById(id)) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        transactionRepository.deleteById(id);
    }

    private TransactionDTO toDTO(Transaction transaction) {
        return new TransactionDTO(
            transaction.getId(),
            transaction.getAccount().getId(),
            transaction.getDate(),
            transaction.getPayee(),
            transaction.getCategoryId(),
            transaction.getAmount(),
            transaction.getMemo(),
            transaction.getCleared()
        );
    }
}
