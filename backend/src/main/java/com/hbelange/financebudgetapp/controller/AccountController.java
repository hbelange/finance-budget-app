package com.hbelange.financebudgetapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.AccountDTO;
import com.hbelange.financebudgetapp.dto.AccountRequest;
import com.hbelange.financebudgetapp.service.AccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    private final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDTO> getAllAccounts() {
        return accountService.findAll();
    }

    @PostMapping
    public AccountDTO createAccount(@Valid @RequestBody AccountRequest req) {
        return accountService.create(req);
    }

    @PutMapping("/{id}")
    public AccountDTO updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountRequest req) {
        return accountService.update(id, req);
    }
    @DeleteMapping("/{id}")
    public String deleteAccount(@PathVariable UUID id) {
        accountService.delete(id);
        return "Account deleted successfully";
    }
}
