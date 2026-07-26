package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.hbelange.financebudgetapp.enums.RolloverType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GoalRequest (
    @NotNull
    UUID categoryId,
    @NotNull
    @PositiveOrZero
    BigDecimal amount,
    @Min(1)
    @Max(31)
    int dayOfMonth,
    @NotNull
    RolloverType rolloverType
) {}
