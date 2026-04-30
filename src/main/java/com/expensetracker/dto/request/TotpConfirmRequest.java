package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TotpConfirmRequest(@NotBlank @Size(min = 6, max = 6) String code) {}
