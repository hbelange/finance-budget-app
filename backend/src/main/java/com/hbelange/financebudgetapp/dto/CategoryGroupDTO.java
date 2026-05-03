package com.hbelange.financebudgetapp.dto;

import java.util.List;
import java.util.UUID;

public record CategoryGroupDTO(
    UUID id,
    String name,
    List<BudgetCategoryViewDTO> categories
) {}
