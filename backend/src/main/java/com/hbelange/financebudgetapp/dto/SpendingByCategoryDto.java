package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SpendingByCategoryDto(UUID categoryId, String categoryName, BigDecimal spent) {}
