package com.expensetracker.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull Integer categoryId,
        @NotNull @DecimalMin("1") BigDecimal amount,
        @NotNull @Pattern(regexp = "\\d{4}-\\d{2}") String yearMonth
) {}
