package com.hbelange.financebudgetapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public List<AccountDTO> getAllAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accountService.findAll(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDTO createAccount(@Valid @RequestBody AccountRequest req, @AuthenticationPrincipal Jwt jwt) {
        return accountService.create(req, jwt.getSubject());
    }

    @PutMapping("/{id}")
    public AccountDTO updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountRequest req, @AuthenticationPrincipal Jwt jwt) {
        return accountService.update(id, req, jwt.getSubject());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        accountService.delete(id, jwt.getSubject());
    }
}
