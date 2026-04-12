package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.hbelange.financebudgetapp.enums.AccountType;

public record AccountDTO(
    UUID id,
    String name,
    AccountType type,
    BigDecimal balance
) {}
