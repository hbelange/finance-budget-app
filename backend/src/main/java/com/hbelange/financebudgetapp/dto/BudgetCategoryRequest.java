package com.hbelange.financebudgetapp.dto;

import jakarta.validation.constraints.NotBlank;

public record BudgetCategoryRequest(@NotBlank String name) {}
