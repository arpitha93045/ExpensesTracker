package com.expensetracker.service;

import com.expensetracker.dto.response.BudgetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    @Value("${app.base-url:http://localhost}")
    private String baseUrl;

    // ── Upload Complete ───────────────────────────────────────────────────────

    public void sendUploadComplete(String toEmail, String fullName,
                                   String fileName, int txCount, boolean success, String errorMsg,
                                   boolean userNotificationsEnabled) {
        if (!userNotificationsEnabled) return;
        String subject = success
                ? "✅ Upload complete — " + txCount + " transactions imported"
                : "❌ Upload failed — " + fileName;

        String status = success
                ? "<span style='color:#16a34a;font-weight:700;'>Completed successfully</span>"
                : "<span style='color:#dc2626;font-weight:700;'>Failed</span>";

        String detail = success
                ? "<p style='margin:8px 0;'>We imported <strong>" + txCount + " transaction(s)</strong> from <code>" + fileName + "</code>.</p>"
                : "<p style='margin:8px 0;'>File: <code>" + fileName + "</code><br>Error: " + errorMsg + "</p>";

        String body = baseTemplate(fullName,
                "Your file upload is " + status,
                detail +
                "<p style='margin:16px 0 0;'>" +
                "<a href='" + baseUrl + "/transactions' style='" + btnStyle("#667eea") + "'>View Transactions</a>" +
                "</p>");

        emailService.sendHtml(toEmail, subject, body);
    }

    // ── Budget Overspend Alert ────────────────────────────────────────────────

    public void sendBudgetAlert(String toEmail, String fullName,
                                String categoryName, double pct, BigDecimal spent, BigDecimal budget,
                                String yearMonth, boolean userNotificationsEnabled) {
        if (!userNotificationsEnabled) return;
        boolean over = pct >= 100;
        String subject = over
                ? "🚨 Budget exceeded — " + categoryName + " (" + yearMonth + ")"
                : "⚠️ Budget alert — " + categoryName + " at " + Math.round(pct) + "%";

        String color = over ? "#dc2626" : "#d97706";
        String heading = over
                ? "You've <span style='color:" + color + ";'>exceeded</span> your " + categoryName + " budget"
                : "You've used <span style='color:" + color + ";'>" + Math.round(pct) + "%</span> of your " + categoryName + " budget";

        String body = baseTemplate(fullName, heading,
                "<table style='width:100%;border-collapse:collapse;margin:12px 0;'>" +
                "<tr><td style='padding:6px 0;color:#6b7280;'>Spent</td>" +
                "<td style='padding:6px 0;text-align:right;font-weight:700;color:" + color + ";'>₹" + String.format("%,.0f", spent) + "</td></tr>" +
                "<tr><td style='padding:6px 0;color:#6b7280;'>Budget</td>" +
                "<td style='padding:6px 0;text-align:right;font-weight:700;'>₹" + String.format("%,.0f", budget) + "</td></tr>" +
                "<tr><td style='padding:6px 0;color:#6b7280;'>Period</td>" +
                "<td style='padding:6px 0;text-align:right;'>" + yearMonth + "</td></tr>" +
                "</table>" +
                progressBar(pct, color) +
                "<p style='margin:16px 0 0;'>" +
                "<a href='" + baseUrl + "/dashboard' style='" + btnStyle(color) + "'>View Dashboard</a>" +
                "</p>");

        emailService.sendHtml(toEmail, subject, body);
    }

    // ── Monthly Summary ───────────────────────────────────────────────────────

    public void sendMonthlySummary(String toEmail, String fullName, String yearMonth,
                                   BigDecimal totalExpenses, BigDecimal totalIncome,
                                   List<BudgetResponse> budgets, boolean userNotificationsEnabled) {
        if (!userNotificationsEnabled) return;
        String subject = "📊 Your monthly summary — " + yearMonth;

        StringBuilder budgetRows = new StringBuilder();
        for (BudgetResponse b : budgets) {
            String color = b.percentUsed() >= 100 ? "#dc2626" : b.percentUsed() >= 80 ? "#d97706" : "#16a34a";
            budgetRows.append(
                "<tr>" +
                "<td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>" + b.categoryName() + "</td>" +
                "<td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;text-align:right;'>₹" + String.format("%,.0f", b.spentAmount()) + "</td>" +
                "<td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;text-align:right;'>₹" + String.format("%,.0f", b.budgetAmount()) + "</td>" +
                "<td style='padding:8px 12px;border-bottom:1px solid #e5e7eb;text-align:right;color:" + color + ";font-weight:700;'>" + Math.round(b.percentUsed()) + "%</td>" +
                "</tr>");
        }

        String budgetTable = budgets.isEmpty() ? "<p style='color:#6b7280;'>No budgets set for this month.</p>" :
                "<table style='width:100%;border-collapse:collapse;margin:12px 0;font-size:14px;'>" +
                "<thead><tr style='background:#f9fafb;'>" +
                "<th style='padding:8px 12px;text-align:left;color:#6b7280;font-weight:600;'>Category</th>" +
                "<th style='padding:8px 12px;text-align:right;color:#6b7280;font-weight:600;'>Spent</th>" +
                "<th style='padding:8px 12px;text-align:right;color:#6b7280;font-weight:600;'>Budget</th>" +
                "<th style='padding:8px 12px;text-align:right;color:#6b7280;font-weight:600;'>Used</th>" +
                "</tr></thead><tbody>" + budgetRows + "</tbody></table>";

        BigDecimal net = totalIncome.subtract(totalExpenses);
        String netColor = net.compareTo(BigDecimal.ZERO) >= 0 ? "#16a34a" : "#dc2626";

        String body = baseTemplate(fullName, "Your spending summary for <strong>" + yearMonth + "</strong>",
                "<table style='width:100%;border-collapse:collapse;margin:12px 0;'>" +
                "<tr><td style='padding:8px 0;color:#6b7280;'>Total Expenses</td>" +
                "<td style='padding:8px 0;text-align:right;font-weight:700;color:#dc2626;'>₹" + String.format("%,.0f", totalExpenses) + "</td></tr>" +
                "<tr><td style='padding:8px 0;color:#6b7280;'>Total Income</td>" +
                "<td style='padding:8px 0;text-align:right;font-weight:700;color:#16a34a;'>₹" + String.format("%,.0f", totalIncome) + "</td></tr>" +
                "<tr style='border-top:2px solid #e5e7eb;'><td style='padding:8px 0;font-weight:700;'>Net Savings</td>" +
                "<td style='padding:8px 0;text-align:right;font-weight:700;color:" + netColor + ";'>₹" + String.format("%,.0f", net) + "</td></tr>" +
                "</table>" +
                "<h3 style='margin:20px 0 8px;font-size:15px;color:#111827;'>Budget Performance</h3>" +
                budgetTable +
                "<p style='margin:16px 0 0;'>" +
                "<a href='" + baseUrl + "/dashboard' style='" + btnStyle("#667eea") + "'>View Full Report</a>" +
                "</p>");

        emailService.sendHtml(toEmail, subject, body);
    }

    // ── HTML helpers ──────────────────────────────────────────────────────────

    private String baseTemplate(String fullName, String heading, String content) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f9fafb;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;'>" +
                "<div style='max-width:560px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.08);'>" +
                "<div style='background:linear-gradient(135deg,#667eea,#764ba2);padding:28px 32px;'>" +
                "<h1 style='margin:0;color:#ffffff;font-size:20px;font-weight:700;'>💳 SmartExpense</h1>" +
                "</div>" +
                "<div style='padding:28px 32px;'>" +
                "<p style='margin:0 0 4px;color:#6b7280;font-size:13px;'>Hi " + fullName + ",</p>" +
                "<h2 style='margin:8px 0 16px;font-size:18px;color:#111827;line-height:1.4;'>" + heading + "</h2>" +
                content +
                "</div>" +
                "<div style='padding:16px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center;'>" +
                "<p style='margin:0;color:#9ca3af;font-size:12px;'>SmartExpense · You're receiving this because notifications are enabled on your account.</p>" +
                "</div></div></body></html>";
    }

    private String progressBar(double pct, String color) {
        double capped = Math.min(pct, 100);
        return "<div style='background:#e5e7eb;border-radius:4px;height:8px;margin:8px 0;overflow:hidden;'>" +
                "<div style='background:" + color + ";width:" + capped + "%;height:100%;border-radius:4px;'></div>" +
                "</div><p style='margin:4px 0;font-size:12px;color:#6b7280;text-align:right;'>" + Math.round(pct) + "% used</p>";
    }

    private String btnStyle(String color) {
        return "display:inline-block;background:" + color + ";color:#ffffff;padding:10px 24px;" +
                "border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;";
    }
}
