package com.hbelange.financebudgetapp.dto;

import java.util.List;
import java.util.UUID;

public record BudgetGroupDTO(
    UUID id,
    String name,
    List<BudgetCategoryViewDTO> categories
) {}
