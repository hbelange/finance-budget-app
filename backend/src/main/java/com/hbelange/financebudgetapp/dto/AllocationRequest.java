package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AllocationRequest(
    @NotNull UUID categoryId,
    @NotBlank String month,
    @NotNull BigDecimal assigned
) {}
