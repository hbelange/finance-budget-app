package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryAssigned(UUID categoryId, BigDecimal assigned) {}
