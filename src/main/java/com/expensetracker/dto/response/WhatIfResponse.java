package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record WhatIfResponse(
        BigDecimal currentMonthlyAvg,
        BigDecimal savedPerMonth,
        BigDecimal savedIn1Month,
        BigDecimal savedIn3Months,
        BigDecimal savedIn6Months,
        BigDecimal savedIn12Months,
        List<GoalEquivalent> goalEquivalents
) {
    public record GoalEquivalent(String name, BigDecimal cost, double achievableInMonths) {}
}
