package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TaxReportResponse(
    int taxYear,
    BigDecimal totalDeductible,
    List<DeductibleCategory> categories
) {
    public record DeductibleCategory(
        String name,
        String icon,
        String color,
        BigDecimal total,
        int transactionCount,
        List<TaxTransaction> transactions
    ) {}

    public record TaxTransaction(
        String date,
        String description,
        String merchant,
        BigDecimal amount,
        String currency
    ) {}
}
