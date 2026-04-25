package com.expensetracker.controller;

import com.expensetracker.dto.response.*;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.AnalyticsService;
import com.expensetracker.service.AutopsyService;
import com.expensetracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Expense analytics and insights APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AutopsyService autopsyService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @GetMapping("/summary")
    @Operation(summary = "Get expense summary with category breakdown, trends, and AI insights")
    public AnalyticsSummaryResponse getSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        UUID userId = resolveUserId(userDetails);
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);
        return analyticsService.getSummary(userId, effectiveFrom, effectiveTo);
    }

    @GetMapping("/bills")
    @Operation(summary = "Predict upcoming recurring bills based on transaction history")
    public BillPredictionResponse getBills(@AuthenticationPrincipal UserDetails userDetails) {
        return analyticsService.getBillPredictions(resolveUserId(userDetails));
    }

    @GetMapping("/inflation")
    @Operation(summary = "Detect lifestyle inflation — compare recent 3 months vs 6 months prior")
    public InflationResponse getInflation(@AuthenticationPrincipal UserDetails userDetails) {
        return analyticsService.getInflation(resolveUserId(userDetails));
    }

    @GetMapping("/whatif")
    @Operation(summary = "What-if savings simulator for a merchant or category")
    public WhatIfResponse getWhatIf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String merchantOrCategory,
            @RequestParam(defaultValue = "50") int cutPercent,
            @RequestParam(defaultValue = "6") int months) {

        return analyticsService.getWhatIf(resolveUserId(userDetails), merchantOrCategory, cutPercent, months);
    }

    @GetMapping("/calendar")
    @Operation(summary = "Daily cash flow for calendar view")
    public List<CalendarDayResponse> getCalendar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return analyticsService.getCalendarFlow(resolveUserId(userDetails), from, to);
    }

    @GetMapping("/calendar/day")
    @Operation(summary = "All transactions for a specific day")
    public List<TransactionResponse> getDayTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        UUID userId = resolveUserId(userDetails);
        return transactionRepository.findByUserIdAndDate(userId, date)
                .stream()
                .map(transactionService::toResponse)
                .toList();
    }

    @GetMapping("/autopsy")
    @Operation(summary = "AI-generated monthly financial narrative")
    public AutopsyResponse getAutopsy(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String yearMonth) {

        UUID userId = resolveUserId(userDetails);
        YearMonth ym = YearMonth.parse(yearMonth);
        return autopsyService.getAutopsy(userId, ym);
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
