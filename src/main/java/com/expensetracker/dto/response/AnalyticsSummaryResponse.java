package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsSummaryResponse(
        BigDecimal totalExpenses,
        BigDecimal totalIncome,
        BigDecimal netSavings,
        int transactionCount,
        List<CategoryBreakdown> categoryBreakdown,
        List<MonthlyTrend> monthlyTrend,
        List<TopMerchant> topMerchants,
        List<String> insights,
        MomComparison momComparison
) {
    public record CategoryBreakdown(String category, String icon, String color, BigDecimal total, long count, double percentage, Double momChange) {}
    public record MonthlyTrend(String month, BigDecimal expenses, BigDecimal income) {}
    public record TopMerchant(String merchant, long count, BigDecimal total) {}

    /** Month-over-month comparison: current vs previous month totals */
    public record MomComparison(
            BigDecimal currentExpenses,
            BigDecimal previousExpenses,
            double expensesChangePercent,
            BigDecimal currentIncome,
            BigDecimal previousIncome,
            double incomeChangePercent
    ) {}
}
