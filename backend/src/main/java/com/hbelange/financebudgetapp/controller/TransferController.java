package com.hbelange.financebudgetapp.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hbelange.financebudgetapp.dto.TransactionDTO;
import com.hbelange.financebudgetapp.dto.TransferRequest;
import com.hbelange.financebudgetapp.service.TransferService;

import org.springframework.http.HttpStatus; 

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    
    private final TransferService transferService;

    @Autowired
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionDTO> createTransfer(@Valid @RequestBody TransferRequest req, @AuthenticationPrincipal Jwt jwt) {
        String userSub = jwt.getSubject();
        return transferService.createTransfer(req, userSub);
    }

    @PutMapping("/{id}")
    public List<TransactionDTO> updateTransfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest req, @AuthenticationPrincipal Jwt jwt) {
        String userSub = jwt.getSubject();
        return transferService.updateTransfer(id, req, userSub);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransfer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userSub = jwt.getSubject();
        transferService.deleteTransfer(id, userSub);
    }
}
