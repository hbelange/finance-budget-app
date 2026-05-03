package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorySpent(UUID categoryId, BigDecimal spent) {}
