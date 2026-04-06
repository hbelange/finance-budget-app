package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TransactionRequest (
    @NotNull UUID accountId,
    @NotNull LocalDate date,
    String payee,
    UUID categoryId,
    @NotNull BigDecimal amount,
    String memo,
    Boolean cleared
){
    
}
