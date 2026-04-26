package com.expensetracker.service;

import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetAlertService {

    private final BudgetService budgetService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private static final double WARN_THRESHOLD = 80.0;

    @Async("fileProcessingExecutor")
    public void checkAndAlert(UUID userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            List<BudgetResponse> budgets = budgetService.getBudgetsWithProgress(userId, yearMonth);

            for (BudgetResponse b : budgets) {
                if (b.percentUsed() >= WARN_THRESHOLD) {
                    notificationService.sendBudgetAlert(
                            user.getEmail(), user.getFullName(),
                            b.categoryName(), b.percentUsed(),
                            b.spentAmount(), b.budgetAmount(), yearMonth,
                            user.isNotificationsEnabled());
                }
            }
        } catch (Exception e) {
            log.error("Budget alert check failed for user {}: {}", userId, e.getMessage());
        }
    }
}
