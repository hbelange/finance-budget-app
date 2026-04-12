package com.hbelange.financebudgetapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.AccountBalance;
import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.entity.Account;
import com.hbelange.financebudgetapp.repository.AccountRepository;

@Service
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountDTO> findAll() {
        List<AccountBalance> balances = accountRepository.findAllBalances();
        return accountRepository.findAll()
            .stream()
            .map(a -> {
                BigDecimal balance = balances.stream()
                    .filter(ab -> ab.accountId().equals(a.getId()))
                    .map(AccountBalance::balance)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
                return toDTO(a, balance);
            })
            .toList();
    }

    public AccountDTO create(AccountRequest req) {
        Account account = new Account();
        account.setName(req.name());
        account.setType(req.type());
        return toDTO(accountRepository.save(account), BigDecimal.ZERO);
    }

    public AccountDTO update(UUID id, AccountRequest req) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setName(req.name());
        account.setType(req.type());
        return toDTO(accountRepository.save(account), accountRepository.findBalanceById(id));
    }

    public void delete(UUID id) {
        if (!accountRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (accountRepository.existsTransactionsByAccountId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account has existing transactions");
        }
        accountRepository.deleteById(id);
    }

    private AccountDTO toDTO(Account account, BigDecimal balance) {
        return new AccountDTO(account.getId(), account.getName(), account.getType().name(), balance);
    }
}
