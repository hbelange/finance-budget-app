package com.hbelange.financebudgetapp.dto;

import java.util.UUID;

public record BudgetCategoryDTO(
    UUID id,
    String name
) {}
