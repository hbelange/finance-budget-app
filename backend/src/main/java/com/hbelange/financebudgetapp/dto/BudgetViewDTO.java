package com.hbelange.financebudgetapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetViewDTO(
    BigDecimal readyToAssign,
    List<CategoryGroupDTO> groups
) {}
