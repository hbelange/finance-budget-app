package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        // Implementation to retrieve all accounts and their balances
        return accountRepository.findAll()
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

    }
    public AccountDTO create(AccountRequest req){
        // Implementation to create a new account
        Account account = new Account();
        account.setName(req.name());
        account.setType(req.type());
        Account savedAccount = accountRepository.save(account);
        return toDTO(savedAccount);
    }
    public AccountDTO update(UUID id, AccountRequest req){
        // Implementation to update an existing account
        Account account = accountRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setName(req.name());
        account.setType(req.type());
        Account savedAccount = accountRepository.save(account);
        return toDTO(savedAccount);
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
    private AccountDTO toDTO(Account account) {
        // Implementation to convert Account entity to AccountDTO, including balance retrieval
        return new AccountDTO(
            account.getId(),
            account.getName(),
            account.getType().name(),
            accountRepository.findBalanceById(account.getId())
        );
    }
}
