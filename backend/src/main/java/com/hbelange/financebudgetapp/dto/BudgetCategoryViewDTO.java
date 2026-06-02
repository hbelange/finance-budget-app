package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetCategoryViewDTO(
    UUID id,
    String name,
    BigDecimal assigned,
    BigDecimal spent,
    BigDecimal available,
    boolean systemManaged
) {}
