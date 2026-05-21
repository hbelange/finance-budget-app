package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransferRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.entity.Transaction;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.TransactionRepository;

import org.springframework.transaction.annotation.Transactional;
@Service
public class TransferService {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public TransferService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }


    @Transactional
    public List<TransactionDTO> createTransfer(TransferRequest req, String userSub) {
        // User must have access to both accounts to make a transfer
        Account toAccount = accountRepository.findById(req.toAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + req.toAccountId()));
        Account fromAccount = accountRepository.findById(req.fromAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + req.fromAccountId()));
        if (!toAccount.getUserSub().equals(userSub) || !fromAccount.getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Transaction toTransaction = createTransaction(toAccount, fromAccount, req, true);

        Transaction fromTransaction = createTransaction(fromAccount, toAccount, req, false);
        
        // Save the "to" transaction first to generate an ID for the transfer
        Transaction savedToTransaction = transactionRepository.save(toTransaction);

        // Link the "from" transaction to the "to" transaction using the transferId field
        fromTransaction.setTransferId(savedToTransaction.getId());
        Transaction savedFromTransaction = transactionRepository.save(fromTransaction);

        // Now update the "to" transaction to link back to the "from" transaction
        savedToTransaction.setTransferId(savedFromTransaction.getId());
        transactionRepository.save(savedToTransaction);
        return Arrays.asList(toDTO(savedToTransaction), toDTO(savedFromTransaction));
    }

    @Transactional
    public void deleteTransfer(UUID transferId, String userSub) {
        Transaction transaction = transactionRepository.findById(transferId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + transferId));
        if (!transaction.getAccount().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (transaction.getTransferId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not part of a transfer: " + transferId);
        }
        // Find the linked transaction and delete both
        Transaction linkedTransaction = transactionRepository.findById(transaction.getTransferId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Linked transaction not found for transfer: " + transferId));
        transactionRepository.delete(transaction);
        transactionRepository.delete(linkedTransaction);
    }

    @Transactional
    public List<TransactionDTO> updateTransfer(UUID id, TransferRequest req, String userSub) {

        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));

        if (!transaction.getAccount().getUserSub().equals(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (transaction.getTransferId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not part of a transfer: " + id);
        }

        Transaction linkedTransaction = transactionRepository.findById(transaction.getTransferId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Linked transaction not found for transfer: " + id));

        // Determine which transaction is the "to" and which is the "from" based on the amount
        boolean isToTransaction = transaction.getAmount().compareTo(BigDecimal.ZERO) > 0;
        Transaction toTransaction = isToTransaction ? transaction : linkedTransaction;
        Transaction fromTransaction = isToTransaction ? linkedTransaction : transaction;

        // Is user changing the accounts involved in the transfer? If so, we need to check permissions and update both transactions
        if (!toTransaction.getAccount().getId().equals(req.toAccountId()) || !fromTransaction.getAccount().getId().equals(req.fromAccountId())) {
            Account newToAccount = accountRepository.findById(req.toAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + req.toAccountId()));
            Account newFromAccount = accountRepository.findById(req.fromAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + req.fromAccountId()));
            if (!newToAccount.getUserSub().equals(userSub) || !newFromAccount.getUserSub().equals(userSub)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            toTransaction.setAccount(newToAccount);
            fromTransaction.setAccount(newFromAccount); 
        }
        
        toTransaction.setDate(req.date());
        toTransaction.setAmount(req.amount());
        toTransaction.setPayee(fromTransaction.getAccount().getName());
        toTransaction.setMemo(req.memo());
        toTransaction.setCleared(req.cleared() != null ? req.cleared() : false);

        fromTransaction.setDate(req.date());
        fromTransaction.setAmount(req.amount().negate());
        fromTransaction.setPayee(toTransaction.getAccount().getName());
        fromTransaction.setMemo(req.memo());
        fromTransaction.setCleared(req.cleared() != null ? req.cleared() : false);

        return Arrays.asList(
            toDTO(transactionRepository.save(toTransaction)),
            toDTO(transactionRepository.save(fromTransaction))
        );

    }

    private Transaction createTransaction(Account account, Account otherAccount, TransferRequest req, boolean isTo) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setDate(req.date());
        transaction.setAmount(isTo ? req.amount() : req.amount().negate());
        transaction.setMemo(req.memo());
        transaction.setCleared(req.cleared() != null ? req.cleared() : false);
        transaction.setPayee(otherAccount.getName());
        return transaction;
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
            transaction.getCleared(),
            transaction.getTransferId()
        );
    }
}
