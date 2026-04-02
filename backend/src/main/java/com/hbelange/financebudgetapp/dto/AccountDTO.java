package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDTO(
    UUID id,
    String name,
    String type,
    BigDecimal balance
) {}
