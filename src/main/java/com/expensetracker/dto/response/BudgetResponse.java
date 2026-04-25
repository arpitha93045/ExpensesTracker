package com.expensetracker.dto.response;

import java.math.BigDecimal;

public record BudgetResponse(
        Integer id,
        Integer categoryId,
        String categoryName,
        String categoryColor,
        BigDecimal budgetAmount,
        BigDecimal spentAmount,
        double percentUsed,
        String yearMonth
) {}
