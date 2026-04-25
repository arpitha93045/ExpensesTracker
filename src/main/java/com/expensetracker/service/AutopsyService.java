package com.expensetracker.service;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.dto.response.AutopsyResponse;
import com.expensetracker.repository.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutopsyService {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${app.anthropic.model:claude-3-5-haiku-20241022}")
    private String model;

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public AutopsyResponse getAutopsy(UUID userId, YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to   = yearMonth.atEndOfMonth();

        List<Transaction> all = transactionRepository.searchAll(userId, from, to, null, null, null);
        List<Transaction> debits = all.stream()
                .filter(t -> "DEBIT".equals(t.getTransactionType().name()))
                .toList();

        BigDecimal totalSpend  = debits.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = all.stream()
                .filter(t -> "CREDIT".equals(t.getTransactionType().name()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Weekly breakdown
        Map<Integer, List<Transaction>> byWeek = debits.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionDate().get(ChronoField.ALIGNED_WEEK_OF_MONTH)));

        List<AutopsyResponse.WeeklyBreakdown> weeklyBreakdown = byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    BigDecimal weekTotal = e.getValue().stream()
                            .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    String topCat = e.getValue().stream()
                            .filter(t -> t.getCategory() != null)
                            .collect(Collectors.groupingBy(t -> t.getCategory().getName(),
                                    Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("Uncategorized");
                    return new AutopsyResponse.WeeklyBreakdown(e.getKey(), weekTotal, topCat);
                }).toList();

        // Highlights: high-spend days
        int daysInMonth = yearMonth.lengthOfMonth();
        BigDecimal dailyAvg = daysInMonth > 0
                ? totalSpend.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<LocalDate, BigDecimal> byDay = debits.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionDate,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        List<AutopsyResponse.Highlight> highlights = new ArrayList<>();

        // High-spend days (> 2x daily average)
        byDay.entrySet().stream()
                .filter(e -> dailyAvg.compareTo(BigDecimal.ZERO) > 0 && e.getValue().compareTo(dailyAvg.multiply(BigDecimal.valueOf(2))) > 0)
                .sorted(Map.Entry.<LocalDate, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> {
                    Transaction biggest = debits.stream()
                            .filter(t -> t.getTransactionDate().equals(e.getKey()))
                            .max(Comparator.comparing(Transaction::getAmount))
                            .orElse(null);
                    if (biggest != null) {
                        highlights.add(new AutopsyResponse.Highlight(
                                e.getKey(),
                                biggest.getDescription(),
                                e.getValue(),
                                "High-spend day — " + String.format("₹%.0f", e.getValue()) + " spent (2× your daily average)"
                        ));
                    }
                });

        // Late-night transactions (10pm - 5am based on createdAt)
        debits.stream()
                .filter(t -> {
                    int hour = t.getCreatedAt().getHour();
                    return hour >= 22 || hour < 5;
                })
                .sorted(Comparator.comparing(Transaction::getAmount).reversed())
                .limit(2)
                .forEach(t -> highlights.add(new AutopsyResponse.Highlight(
                        t.getTransactionDate(),
                        t.getDescription(),
                        t.getAmount(),
                        "Late-night purchase — impulse spending tends to happen after 10pm"
                )));

        // Top categories for prompt
        Map<String, BigDecimal> catTotals = debits.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        List<Map.Entry<String, BigDecimal>> topCats = catTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .toList();

        String narrative = generateNarrative(yearMonth, totalSpend, totalIncome, weeklyBreakdown, topCats, highlights);

        return new AutopsyResponse(narrative, highlights, weeklyBreakdown);
    }

    private String generateNarrative(YearMonth yearMonth, BigDecimal totalSpend, BigDecimal totalIncome,
                                     List<AutopsyResponse.WeeklyBreakdown> weeks,
                                     List<Map.Entry<String, BigDecimal>> topCats,
                                     List<AutopsyResponse.Highlight> highlights) {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            return buildFallbackNarrative(yearMonth, totalSpend, totalIncome, weeks, topCats);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a witty personal finance advisor. Write a 3-4 paragraph narrative about this person's spending month.\n");
        prompt.append("Make it conversational and slightly humorous, like a friend reviewing their finances.\n");
        prompt.append("Be specific about the numbers. Use ₹ for currency.\n\n");
        prompt.append("Month: ").append(yearMonth).append("\n");
        prompt.append("Total spent: ₹").append(totalSpend.toPlainString()).append("\n");
        prompt.append("Total income: ₹").append(totalIncome.toPlainString()).append("\n");
        prompt.append("Net savings: ₹").append(totalIncome.subtract(totalSpend).toPlainString()).append("\n\n");

        prompt.append("Weekly breakdown:\n");
        for (AutopsyResponse.WeeklyBreakdown w : weeks) {
            prompt.append(String.format("Week %d: ₹%.0f | Top category: %s%n", w.week(), w.totalSpend(), w.topCategory()));
        }

        if (!topCats.isEmpty()) {
            prompt.append("\nTop spending categories:\n");
            for (int i = 0; i < topCats.size(); i++) {
                prompt.append(String.format("%d. %s: ₹%.0f%n", i + 1, topCats.get(i).getKey(), topCats.get(i).getValue()));
            }
        }

        if (!highlights.isEmpty()) {
            prompt.append("\nNotable events:\n");
            for (AutopsyResponse.Highlight h : highlights) {
                prompt.append(String.format("- %s: %s (₹%.0f)%n", h.date(), h.description(), h.amount()));
            }
        }

        prompt.append("\nReturn ONLY a JSON object: {\"narrative\": \"your narrative here with \\n\\n between paragraphs\"}");

        try {
            String responseText = callClaudeApi(prompt.toString());
            int start = responseText.indexOf('{');
            int end   = responseText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                Map<String, Object> parsed = objectMapper.readValue(
                        responseText.substring(start, end + 1), new TypeReference<>() {});
                Object narrativeObj = parsed.get("narrative");
                if (narrativeObj instanceof String s && !s.isBlank()) return s;
            }
            return responseText;
        } catch (Exception e) {
            log.warn("Autopsy Claude API call failed, using fallback: {}", e.getMessage());
            return buildFallbackNarrative(yearMonth, totalSpend, totalIncome, weeks, topCats);
        }
    }

    private String buildFallbackNarrative(YearMonth yearMonth, BigDecimal totalSpend, BigDecimal totalIncome,
                                          List<AutopsyResponse.WeeklyBreakdown> weeks,
                                          List<Map.Entry<String, BigDecimal>> topCats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Here's your financial summary for %s. You spent ₹%.0f this month", yearMonth, totalSpend));
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savings = totalIncome.subtract(totalSpend);
            sb.append(String.format(" against an income of ₹%.0f, ", totalIncome));
            if (savings.compareTo(BigDecimal.ZERO) >= 0) {
                sb.append(String.format("leaving you with ₹%.0f in savings.", savings));
            } else {
                sb.append(String.format("putting you ₹%.0f over budget.", savings.abs()));
            }
        } else {
            sb.append(".");
        }

        if (!topCats.isEmpty()) {
            sb.append("\n\nYour top spending categories were: ");
            sb.append(topCats.stream()
                    .map(e -> String.format("%s (₹%.0f)", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", ")));
            sb.append(".");
        }

        if (!weeks.isEmpty()) {
            AutopsyResponse.WeeklyBreakdown topWeek = weeks.stream()
                    .max(Comparator.comparing(AutopsyResponse.WeeklyBreakdown::totalSpend))
                    .orElse(weeks.get(0));
            sb.append(String.format("\n\nYour heaviest spending week was Week %d with ₹%.0f spent, mostly on %s.",
                    topWeek.week(), topWeek.totalSpend(), topWeek.topCategory()));
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String callClaudeApi(String prompt) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 1024);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        String json = objectMapper.writeValueAsString(requestBody);
        Request request = new Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", anthropicApiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Claude API error: " + response.code());
            }
            Map<String, Object> responseMap = objectMapper.readValue(
                    response.body().string(), new TypeReference<>() {});
            List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");
            return (String) content.get(0).get("text");
        }
    }
}
