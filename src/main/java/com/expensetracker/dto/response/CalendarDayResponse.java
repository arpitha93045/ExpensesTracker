package com.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalendarDayResponse(
        LocalDate date,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netFlow,
        long transactionCount
) {}
