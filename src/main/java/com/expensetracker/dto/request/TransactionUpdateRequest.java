package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotNull;

public record TransactionUpdateRequest(
        @NotNull Integer categoryId,
        String categorizationNote
) {}
