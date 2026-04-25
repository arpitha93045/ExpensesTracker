package com.expensetracker.service;

import com.expensetracker.dto.response.*;
import com.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;

    public AnalyticsSummaryResponse getSummary(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> categoryData = transactionRepository.getCategoryBreakdown(userId, from, to);
        List<Object[]> monthlyData  = transactionRepository.getMonthlyTrend(userId, from.minusMonths(11));
        List<Object[]> merchantData = transactionRepository.getTopMerchants(userId, from, to, PageRequest.of(0, 10));

        // Previous month for MoM comparison
        LocalDate prevFrom = from.minusMonths(1);
        LocalDate prevTo   = to.minusMonths(1);
        List<Object[]> prevCategoryData = transactionRepository.getCategoryBreakdown(userId, prevFrom, prevTo);

        BigDecimal totalExpenses = sum(categoryData, 1);

        // Income from the selected month's credit transactions
        List<Object[]> monthlyInRange = transactionRepository.getMonthlyTrend(userId, from);
        String targetMonth = String.format("%04d-%02d", from.getYear(), from.getMonthValue());
        BigDecimal totalIncome = monthlyInRange.stream()
                .filter(r -> targetMonth.equals(r[0]))
                .map(r -> (BigDecimal) r[2])
                .findFirst().orElse(BigDecimal.ZERO);

        // Previous month totals
        BigDecimal prevExpenses = sum(prevCategoryData, 1);
        List<Object[]> prevMonthInRange = transactionRepository.getMonthlyTrend(userId, prevFrom);
        String prevMonth = String.format("%04d-%02d", prevFrom.getYear(), prevFrom.getMonthValue());
        BigDecimal prevIncome = prevMonthInRange.stream()
                .filter(r -> prevMonth.equals(r[0]))
                .map(r -> (BigDecimal) r[2])
                .findFirst().orElse(BigDecimal.ZERO);

        // Category MoM map: category name -> previous total
        Map<String, BigDecimal> prevCatMap = prevCategoryData.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));

        List<AnalyticsSummaryResponse.CategoryBreakdown> breakdowns = categoryData.stream()
                .map(row -> {
                    String category = (String) row[0];
                    BigDecimal total = (BigDecimal) row[1];
                    long count = ((Number) row[2]).longValue();
                    double pct = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                            ? total.divide(totalExpenses, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
                    BigDecimal prev = prevCatMap.getOrDefault(category, null);
                    Double momChange = null;
                    if (prev != null && prev.compareTo(BigDecimal.ZERO) > 0) {
                        momChange = total.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                    }
                    return new AnalyticsSummaryResponse.CategoryBreakdown(category, null, null, total, count, pct, momChange);
                }).toList();

        List<AnalyticsSummaryResponse.MonthlyTrend> trends = monthlyData.stream()
                .map(row -> new AnalyticsSummaryResponse.MonthlyTrend(
                        (String) row[0], (BigDecimal) row[1], (BigDecimal) row[2])).toList();

        List<AnalyticsSummaryResponse.TopMerchant> merchants = merchantData.stream()
                .map(row -> new AnalyticsSummaryResponse.TopMerchant(
                        (String) row[0], ((Number) row[1]).longValue(), (BigDecimal) row[2])).toList();

        AnalyticsSummaryResponse.MomComparison mom = new AnalyticsSummaryResponse.MomComparison(
                totalExpenses, prevExpenses, pctChange(totalExpenses, prevExpenses),
                totalIncome, prevIncome, pctChange(totalIncome, prevIncome)
        );

        List<String> insights = generateInsights(breakdowns, trends, totalExpenses, mom);

        return new AnalyticsSummaryResponse(
                totalExpenses, totalIncome,
                totalIncome.subtract(totalExpenses),
                breakdowns.stream().mapToInt(b -> (int) b.count()).sum(),
                breakdowns, trends, merchants, insights, mom
        );
    }

    // ── Bill Due Predictor ───────────────────────────────────────

    public BillPredictionResponse getBillPredictions(UUID userId) {
        LocalDate since = LocalDate.now().minusMonths(6);
        List<Object[]> rows = transactionRepository.getMerchantTransactionHistory(userId, since);

        Map<String, List<Object[]>> byMerchant = rows.stream()
                .collect(Collectors.groupingBy(r -> (String) r[0]));

        List<BillPredictionResponse.Bill> bills = new ArrayList<>();

        for (Map.Entry<String, List<Object[]>> entry : byMerchant.entrySet()) {
            String merchant = entry.getKey();
            List<Object[]> merchantRows = entry.getValue();
            if (merchantRows.size() < 2) continue;

            List<LocalDate> dates = merchantRows.stream()
                    .map(r -> ((java.sql.Date) r[2]).toLocalDate())
                    .sorted()
                    .toList();

            BigDecimal avgAmount = merchantRows.stream()
                    .map(r -> (BigDecimal) r[1])
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(merchantRows.size()), 2, RoundingMode.HALF_UP);

            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < dates.size(); i++) {
                gaps.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
            }

            double avgGap = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
            double stdDev = computeStdDev(gaps, avgGap);
            double cv = avgGap > 0 ? stdDev / avgGap : 1.0;

            String frequency;
            if      (avgGap >= 28 && avgGap <= 33 && cv < 0.20) frequency = "MONTHLY";
            else if (avgGap >= 6  && avgGap <= 8  && cv < 0.20) frequency = "WEEKLY";
            else if (avgGap >= 13 && avgGap <= 16 && cv < 0.20) frequency = "BIWEEKLY";
            else continue;

            double confidence = Math.min(1.0, (merchantRows.size() / 6.0) * (1.0 - cv));
            LocalDate lastDate = dates.get(dates.size() - 1);
            LocalDate nextDue  = lastDate.plusDays(Math.round(avgGap));

            if (!nextDue.isBefore(LocalDate.now()) && !nextDue.isAfter(LocalDate.now().plusDays(30))) {
                bills.add(new BillPredictionResponse.Bill(
                        merchant, avgAmount, frequency, lastDate, nextDue, confidence));
            }
        }

        bills.sort(Comparator.comparing(BillPredictionResponse.Bill::nextDueDate));
        return new BillPredictionResponse(bills);
    }

    private double computeStdDev(List<Long> gaps, double mean) {
        if (gaps.size() < 2) return 0;
        double variance = gaps.stream().mapToDouble(g -> Math.pow(g - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    // ── Lifestyle Inflation Detector ─────────────────────────────

    public InflationResponse getInflation(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate nowTo   = today.withDayOfMonth(1).minusDays(1);
        LocalDate nowFrom = nowTo.withDayOfMonth(1).minusMonths(2);
        LocalDate thenTo   = nowFrom.minusDays(1);
        LocalDate thenFrom = thenTo.withDayOfMonth(1).minusMonths(2);

        List<Object[]> nowData  = transactionRepository.getCategoryMonthlyAvg(userId, nowFrom, nowTo);
        List<Object[]> thenData = transactionRepository.getCategoryMonthlyAvg(userId, thenFrom, thenTo);

        Map<String, BigDecimal> thenMap = thenData.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));

        List<InflationResponse.CategoryInflation> categories = nowData.stream()
                .filter(r -> thenMap.containsKey(r[0]))
                .map(r -> {
                    String name  = (String) r[0];
                    BigDecimal now_  = (BigDecimal) r[1];
                    BigDecimal then_ = thenMap.get(name);
                    BigDecimal change = now_.subtract(then_);
                    double pct = then_.compareTo(BigDecimal.ZERO) > 0
                            ? change.divide(then_, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
                    return new InflationResponse.CategoryInflation(name, then_, now_, change, pct);
                })
                .sorted(Comparator.comparingDouble(ci -> -ci.changePercent()))
                .toList();

        BigDecimal creep = categories.stream()
                .filter(c -> c.change().compareTo(BigDecimal.ZERO) > 0)
                .map(InflationResponse.CategoryInflation::change)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNow  = nowData.stream().map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalThen = thenData.stream()
                .filter(r -> nowData.stream().anyMatch(n -> n[0].equals(r[0])))
                .map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        double overall = pctChange(totalNow, totalThen);

        List<Object[]> newMerchantRows = transactionRepository.getNewMerchants(
                userId, nowFrom, nowTo, thenFrom, thenTo);
        List<InflationResponse.NewSubscription> newSubs = newMerchantRows.stream()
                .map(r -> new InflationResponse.NewSubscription(
                        (String) r[0],
                        ((java.sql.Date) r[1]).toLocalDate(),
                        new BigDecimal(r[2].toString()).setScale(2, RoundingMode.HALF_UP)
                )).toList();

        return new InflationResponse(overall, categories, newSubs, creep);
    }

    // ── What-If Simulator ────────────────────────────────────────

    private static final List<Map.Entry<String, BigDecimal>> GOALS = List.of(
            Map.entry("Goa trip",                    new BigDecimal("15000")),
            Map.entry("iPhone 16",                   new BigDecimal("80000")),
            Map.entry("MacBook Air",                 new BigDecimal("99000")),
            Map.entry("DSLR Camera",                 new BigDecimal("45000")),
            Map.entry("Emergency fund (1 month)",    new BigDecimal("30000")),
            Map.entry("Noise-cancelling headphones", new BigDecimal("25000")),
            Map.entry("Europe trip",                 new BigDecimal("200000")),
            Map.entry("Gaming console",              new BigDecimal("50000"))
    );

    public WhatIfResponse getWhatIf(UUID userId, String term, int cutPercent, int lookbackMonths) {
        LocalDate since = LocalDate.now().minusMonths(lookbackMonths);

        BigDecimal byMerchant = transactionRepository.getMonthlyAvgByMerchant(userId, term, since);
        BigDecimal byCat      = transactionRepository.getMonthlyAvgByCategory(userId, term, since);

        BigDecimal monthlyAvg = (byMerchant != null && byMerchant.compareTo(BigDecimal.ZERO) > 0)
                ? byMerchant : (byCat != null ? byCat : BigDecimal.ZERO);

        BigDecimal cutFraction = BigDecimal.valueOf(cutPercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal savedPerMonth = monthlyAvg.multiply(cutFraction).setScale(2, RoundingMode.HALF_UP);

        List<WhatIfResponse.GoalEquivalent> equivalents = GOALS.stream()
                .filter(g -> savedPerMonth.compareTo(BigDecimal.ZERO) > 0)
                .map(g -> {
                    double months = g.getValue().divide(savedPerMonth, 2, RoundingMode.HALF_UP).doubleValue();
                    return new WhatIfResponse.GoalEquivalent(g.getKey(), g.getValue(), months);
                })
                .filter(ge -> ge.achievableInMonths() <= 60)
                .sorted(Comparator.comparingDouble(WhatIfResponse.GoalEquivalent::achievableInMonths))
                .limit(4)
                .toList();

        return new WhatIfResponse(
                monthlyAvg, savedPerMonth,
                savedPerMonth,
                savedPerMonth.multiply(BigDecimal.valueOf(3)),
                savedPerMonth.multiply(BigDecimal.valueOf(6)),
                savedPerMonth.multiply(BigDecimal.valueOf(12)),
                equivalents
        );
    }

    // ── Cash Flow Calendar ───────────────────────────────────────

    public List<CalendarDayResponse> getCalendarFlow(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = transactionRepository.getDailyFlow(userId, from, to);
        return rows.stream().map(r -> new CalendarDayResponse(
                ((java.sql.Date) r[0]).toLocalDate(),
                (BigDecimal) r[1],
                (BigDecimal) r[2],
                (BigDecimal) r[3],
                ((Number) r[4]).longValue()
        )).toList();
    }

    // ── Shared helpers ───────────────────────────────────────────

    double pctChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return 0;
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).doubleValue() * 100;
    }

    BigDecimal sum(List<Object[]> rows, int col) {
        return rows.stream().map(r -> (BigDecimal) r[col]).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String> generateInsights(
            List<AnalyticsSummaryResponse.CategoryBreakdown> breakdowns,
            List<AnalyticsSummaryResponse.MonthlyTrend> trends,
            BigDecimal totalExpenses,
            AnalyticsSummaryResponse.MomComparison mom) {

        List<String> insights = new ArrayList<>();

        if (!breakdowns.isEmpty()) {
            var top = breakdowns.get(0);
            insights.add(String.format("Top spending category: %s (%.1f%% of total)", top.category(), top.percentage()));
        }

        if (mom.expensesChangePercent() > 15) {
            insights.add(String.format("Spending is up %.1f%% vs last month — review recent transactions", mom.expensesChangePercent()));
        } else if (mom.expensesChangePercent() < -15) {
            insights.add(String.format("Great! Spending is down %.1f%% vs last month", Math.abs(mom.expensesChangePercent())));
        }

        breakdowns.stream()
                .filter(b -> b.category().toLowerCase().contains("food") && b.percentage() > 30)
                .findFirst()
                .ifPresent(b -> insights.add(String.format(
                        "Food & Dining is %.1f%% of expenses — meal planning can help reduce this", b.percentage())));

        breakdowns.stream()
                .filter(b -> b.momChange() != null && b.momChange() > 50)
                .findFirst()
                .ifPresent(b -> insights.add(String.format(
                        "%s spending jumped %.1f%% vs last month", b.category(), b.momChange())));

        return insights;
    }
}
