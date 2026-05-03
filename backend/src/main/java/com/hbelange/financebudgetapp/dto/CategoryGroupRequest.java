package com.hbelange.financebudgetapp.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryGroupRequest(@NotBlank String name) {}
