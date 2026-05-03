package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;

public record DashboardDto(
        BigDecimal netWorth,
        BigDecimal incomeThisMonth,
        BigDecimal spentThisMonth) {}
