package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionDTO (
    UUID id,
    UUID accountId,
    LocalDate date,
    String payee,
    UUID categoryId,
    BigDecimal amount,
    String memo,
    Boolean cleared
){}
