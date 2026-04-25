package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InflationResponse(
        double overallInflationPercent,
        List<CategoryInflation> categories,
        List<NewSubscription> newSubscriptions,
        BigDecimal totalLifestyleCreep
) {
    public record CategoryInflation(
            String name,
            BigDecimal then,
            BigDecimal now,
            BigDecimal change,
            double changePercent
    ) {}

    public record NewSubscription(
            String merchant,
            LocalDate since,
            BigDecimal monthlyAmount
    ) {}
}
