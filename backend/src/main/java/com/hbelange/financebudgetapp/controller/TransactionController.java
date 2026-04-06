package com.hbelange.financebudgetapp.controller;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
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
    public TransactionDTO createTransaction(@RequestBody @Valid TransactionRequest req) {
        return transactionService.create(req);
    }

    @PutMapping("/{id}")
    public TransactionDTO updateTransaction(@PathVariable UUID id, @RequestBody @Valid TransactionRequest req) {
        return transactionService.update(id, req);
    }

    @GetMapping
    public String getTransactions(@RequestBody @Valid TransactionRequest req,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) YearMonth month
    ) {
        
    }
}
