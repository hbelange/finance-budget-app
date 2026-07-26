package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.hbelange.financebudgetapp.enums.RolloverType;

public record GoalDTO (
    UUID id,
    UUID categoryId,
    BigDecimal amount,
    int dayOfMonth,
    RolloverType rolloverType
) {}
