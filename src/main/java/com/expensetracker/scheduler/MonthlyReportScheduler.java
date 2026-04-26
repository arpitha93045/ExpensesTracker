package com.expensetracker.scheduler;

import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.response.AnalyticsSummaryResponse;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.AnalyticsService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final NotificationService notificationService;

    // Runs at 09:00 on the 1st of every month
    @Scheduled(cron = "0 0 9 1 * *")
    public void sendMonthlyReports() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String yearMonth = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate from = lastMonth.atDay(1);
        LocalDate to = lastMonth.atEndOfMonth();

        log.info("Sending monthly reports for {}", yearMonth);
        List<User> users = userRepository.findAll();

        for (User user : users) {
            try {
                AnalyticsSummaryResponse summary = analyticsService.getSummary(user.getId(), from, to);
                List<BudgetResponse> budgets = budgetService.getBudgetsWithProgress(user.getId(), yearMonth);
                notificationService.sendMonthlySummary(
                        user.getEmail(), user.getFullName(), yearMonth,
                        summary.totalExpenses(), summary.totalIncome(), budgets,
                        user.isNotificationsEnabled());
            } catch (Exception e) {
                log.error("Failed to send monthly report to {}: {}", user.getEmail(), e.getMessage());
            }
        }
        log.info("Monthly reports sent to {} users", users.size());
    }
}
