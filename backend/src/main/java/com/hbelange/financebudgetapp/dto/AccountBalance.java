package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalance(UUID accountId, BigDecimal balance) {}
