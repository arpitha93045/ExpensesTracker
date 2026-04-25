package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BillPredictionResponse(List<Bill> bills) {
    public record Bill(
            String merchant,
            BigDecimal amount,
            String frequency,
            LocalDate lastCharged,
            LocalDate nextDueDate,
            double confidence
    ) {}
}
