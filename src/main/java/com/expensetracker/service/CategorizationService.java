package com.expensetracker.service;

import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Categorizes transactions using Claude AI with keyword-based fallback.
 *
 * Batching: up to 25 transactions per API call to minimize cost.
 * Fallback: keyword matching if AI fails or returns unparseable response.
 * Prompt design: structured JSON response to avoid parsing ambiguity.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategorizationService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final BudgetAlertService budgetAlertService;

    @Value("${app.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${app.anthropic.model:claude-3-5-haiku-20241022}")
    private String model;

    @Value("${app.anthropic.max-tokens:1024}")
    private int maxTokens;

    @Value("${app.anthropic.batch-size:25}")
    private int batchSize;

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // Keyword-based fallback rules (description -> category name)
    // Covers both Indian and international merchants/apps
    private static final Map<String, String[]> KEYWORD_RULES = Map.ofEntries(
            Map.entry("Food & Dining",   new String[]{
                    "zomato", "swiggy", "dominos", "pizza hut", "mcdonald", "kfc", "subway",
                    "burger king", "café", "cafe", "restaurant", "dining", "bistro", "dhaba",
                    "hotel", "canteen", "eatery", "food", "eat", "kitchen", "biryani",
                    "starbucks", "chaayos", "chai", "bakery", "doordash", "ubereats"}),
            Map.entry("Groceries",       new String[]{
                    "bigbasket", "blinkit", "grofers", "zepto", "dunzo", "dmart", "reliance fresh",
                    "more supermarket", "star bazaar", "nature's basket", "spencer",
                    "walmart", "kroger", "safeway", "grocery", "supermarket", "kirana",
                    "vegetables", "fruits", "provisions"}),
            Map.entry("Transportation",  new String[]{
                    "uber", "ola", "rapido", "namma yatri", "metro", "bmtc", "best bus",
                    "irctc", "redbus", "abhibus", "taxi", "auto", "cab", "petrol", "fuel",
                    "hp petrol", "indian oil", "iocl", "bharat petroleum", "bpcl", "shell",
                    "parking", "fastag", "toll", "lyft", "transit"}),
            Map.entry("Travel",          new String[]{
                    "makemytrip", "goibibo", "cleartrip", "yatra", "easemytrip",
                    "indigo", "air india", "spicejet", "vistara", "akasa",
                    "oyo", "treebo", "fabhotels", "airbnb", "booking.com", "expedia",
                    "airline", "flight", "hotel", "resort", "travel", "holiday"}),
            Map.entry("Shopping",        new String[]{
                    "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq",
                    "snapdeal", "shopclues", "reliance digital", "croma", "vijay sales",
                    "lifestyle", "westside", "pantaloons", "max fashion", "h&m", "zara",
                    "target", "ebay", "etsy", "store", "shop", "clothing", "apparel"}),
            Map.entry("Healthcare",      new String[]{
                    "apollo", "fortis", "manipal", "narayana", "medplus", "netmeds",
                    "pharmeasy", "1mg", "tata 1mg", "pharmacy", "hospital", "clinic",
                    "doctor", "medical", "dental", "health", "lab", "diagnostic",
                    "cvs", "walgreens", "urgent care"}),
            Map.entry("Utilities",       new String[]{
                    "bescom", "msedcl", "tata power", "electricity", "water board", "bbmp",
                    "bwssb", "gas", "piped gas", "indane", "hp gas", "bharatgas",
                    "airtel", "jio", "vi ", "vodafone", "bsnl", "act fibernet", "hathway",
                    "internet", "broadband", "postpaid", "prepaid recharge",
                    "at&t", "verizon", "comcast", "utility", "electric", "cable"}),
            Map.entry("Subscriptions",   new String[]{
                    "netflix", "hotstar", "disney+", "amazon prime", "zee5", "sonyliv",
                    "spotify", "gaana", "wynk", "youtube premium", "apple", "google play",
                    "microsoft", "adobe", "notion", "github", "subscription", "monthly fee",
                    "hulu", "annual plan"}),
            Map.entry("Rent & Housing",  new String[]{
                    "rent", "lease", "maintenance", "society", "housing", "nobroker",
                    "magic bricks", "99acres", "mortgage", "landlord", "property", "pg "}),
            Map.entry("Education",       new String[]{
                    "byju", "unacademy", "vedantu", "upgrad", "coursera", "udemy",
                    "tuition", "university", "college", "school", "institute", "coaching",
                    "exam fee", "textbook", "book", "udemy", "skillshare"}),
            Map.entry("Entertainment",   new String[]{
                    "bookmyshow", "paytm insider", "pvr", "inox", "carnival cinemas",
                    "movie", "cinema", "concert", "event", "ticket", "game", "steam",
                    "playstation", "xbox", "amc", "sports"}),
            Map.entry("Insurance",       new String[]{
                    "lic", "hdfc life", "sbi life", "icici prudential", "bajaj allianz",
                    "star health", "niva bupa", "care insurance", "policybazaar",
                    "insurance", "premium", "policy"}),
            Map.entry("Income",          new String[]{
                    "salary", "neft", "imps", "credited", "redemption", "dividend",
                    "interest", "refund", "cashback", "reversal"})
    );

    @Transactional
    public void categorizeTransactions(List<Transaction> transactions, UUID userId) {
        if (transactions == null || transactions.isEmpty()) return;

        List<Category> categories = categoryRepository.findAllForUser(userId);
        List<String> categoryNames = categories.stream().map(Category::getName).toList();

        List<Transaction> uncategorized = transactions.stream()
                .filter(t -> t.getCategory() == null)
                .toList();

        log.info("Starting categorization for {} transactions", uncategorized.size());

        // Process in batches
        for (int i = 0; i < uncategorized.size(); i += batchSize) {
            List<Transaction> batch = uncategorized.subList(i, Math.min(i + batchSize, uncategorized.size()));
            try {
                if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
                    categorizeBatchWithAI(batch, categories, categoryNames);
                } else {
                    categorizeBatchWithKeywords(batch, categories);
                }
            } catch (Exception e) {
                log.warn("AI categorization failed for batch starting at {}, falling back to keywords: {}", i, e.getMessage());
                categorizeBatchWithKeywords(batch, categories);
            }
        }
        transactionRepository.saveAll(uncategorized);
        log.info("Categorization complete for {} transactions", uncategorized.size());
        budgetAlertService.checkAndAlert(userId);
    }

    private void categorizeBatchWithAI(List<Transaction> batch, List<Category> categories, List<String> categoryNames)
            throws IOException {

        String prompt = buildCategorizationPrompt(batch, categoryNames);
        String responseJson = callClaudeApi(prompt);
        applyAIResults(responseJson, batch, categories);
    }

    private String buildCategorizationPrompt(List<Transaction> batch, List<String> categoryNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a financial transaction categorizer. Classify each transaction into exactly one of these categories:\n");
        sb.append(String.join(", ", categoryNames)).append("\n\n");
        sb.append("Return a JSON array ONLY, no explanation, like:\n");
        sb.append("[{\"id\":0,\"category\":\"Food & Dining\",\"confidence\":0.95},...]\n\n");
        sb.append("Transactions to categorize:\n");

        for (int i = 0; i < batch.size(); i++) {
            Transaction t = batch.get(i);
            sb.append(String.format("[%d] %s | Amount: %.2f | Type: %s%n",
                    i, t.getDescription(), t.getAmount(), t.getTransactionType()));
        }
        return sb.toString();
    }

    private String callClaudeApi(String prompt) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
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
                throw new IOException("Claude API error: " + response.code() + " " + response.message());
            }
            Map<String, Object> responseMap = objectMapper.readValue(
                    response.body().string(), new TypeReference<>() {});
            List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");
            return (String) content.get(0).get("text");
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAIResults(String responseJson, List<Transaction> batch, List<Category> categories) {
        try {
            // Extract JSON array from response (model may add surrounding text)
            int start = responseJson.indexOf('[');
            int end = responseJson.lastIndexOf(']');
            if (start < 0 || end < 0) throw new IllegalArgumentException("No JSON array in response");

            List<Map<String, Object>> results = objectMapper.readValue(
                    responseJson.substring(start, end + 1), new TypeReference<>() {});

            for (Map<String, Object> result : results) {
                int idx = ((Number) result.get("id")).intValue();
                String categoryName = (String) result.get("category");
                double confidence = result.containsKey("confidence")
                        ? ((Number) result.get("confidence")).doubleValue() : 0.8;

                if (idx < 0 || idx >= batch.size()) continue;
                Transaction tx = batch.get(idx);

                categories.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                        .findFirst()
                        .ifPresent(c -> {
                            tx.setCategory(c);
                            tx.setAiCategorized(true);
                            tx.setAiConfidence(BigDecimal.valueOf(confidence));
                        });
            }
        } catch (Exception e) {
            log.error("Failed to parse AI categorization response: {}", e.getMessage());
            throw new RuntimeException("Unparseable AI response", e);
        }
    }

    private void categorizeBatchWithKeywords(List<Transaction> batch, List<Category> categories) {
        for (Transaction tx : batch) {
            String desc = tx.getDescription().toLowerCase();
            String matchedCategory = null;

            outer:
            for (Map.Entry<String, String[]> rule : KEYWORD_RULES.entrySet()) {
                for (String keyword : rule.getValue()) {
                    if (desc.contains(keyword)) {
                        matchedCategory = rule.getKey();
                        break outer;
                    }
                }
            }

            String finalCategory = matchedCategory != null ? matchedCategory : "Other";
            boolean wasMatched = matchedCategory != null;
            categories.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(finalCategory))
                    .findFirst()
                    .ifPresent(c -> {
                        tx.setCategory(c);
                        tx.setAiCategorized(false);
                        tx.setAiConfidence(wasMatched ? BigDecimal.valueOf(0.75) : BigDecimal.valueOf(0.5));
                        tx.setCategorizationNote("keyword-matched");
                    });
        }
    }
}
