package com.hbelange.financebudgetapp.dto;


import com.hbelange.financebudgetapp.enums.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountRequest(
    @NotBlank String name,
    @NotNull AccountType type
) {}
