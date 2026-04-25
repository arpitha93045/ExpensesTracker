package com.expensetracker.dto.response;

import com.expensetracker.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String description,
        BigDecimal amount,
        String currency,
        LocalDate transactionDate,
        TransactionType transactionType,
        String merchant,
        CategoryInfo category,
        boolean aiCategorized,
        BigDecimal aiConfidence,
        String categorizationNote,
        OffsetDateTime createdAt
) {
    public record CategoryInfo(Integer id, String name, String icon, String color) {}
}
