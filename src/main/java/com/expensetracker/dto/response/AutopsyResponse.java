package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AutopsyResponse(
        String narrative,
        List<Highlight> highlights,
        List<WeeklyBreakdown> weeklyBreakdown
) {
    public record Highlight(LocalDate date, String description, BigDecimal amount, String insight) {}
    public record WeeklyBreakdown(int week, BigDecimal totalSpend, String topCategory) {}
}
