package com.hbelange.financebudgetapp.controller;

import java.time.YearMonth;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransactionDateBoundsDto;
import com.hbelange.financebudgetapp.dto.TransactionRequest;
import com.hbelange.financebudgetapp.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @Autowired
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDTO createTransaction(@RequestBody @Valid TransactionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return transactionService.create(req, jwt.getSubject());
    }

    @PutMapping("/{id}")
    public TransactionDTO updateTransaction(@PathVariable UUID id, @RequestBody @Valid TransactionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return transactionService.update(id, req, jwt.getSubject());
    }

    @GetMapping
    public Page<TransactionDTO> getTransactions(
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
        @ParameterObject @PageableDefault(size = 50, sort = "date", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return transactionService.findAll(accountId, month, jwt.getSubject(), pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        transactionService.delete(id, jwt.getSubject());
    }

    @GetMapping("/date-bounds")
    public TransactionDateBoundsDto getDateBounds() {
        return transactionService.getDateBounds();
    }
}
