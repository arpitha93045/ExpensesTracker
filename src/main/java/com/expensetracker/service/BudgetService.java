package com.expensetracker.service;

import com.expensetracker.domain.entity.Budget;
import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.request.BudgetRequest;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public List<BudgetResponse> getBudgetsWithProgress(UUID userId, String yearMonth) {
        List<Object[]> rows = budgetRepository.getBudgetProgress(userId, yearMonth);
        return rows.stream().map(row -> {
            Integer catId = ((Number) row[0]).intValue();
            BigDecimal budgetAmt = (BigDecimal) row[1];
            BigDecimal spent = (BigDecimal) row[2];
            Category cat = categoryRepository.findById(catId).orElseThrow();
            double pct = budgetAmt.compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(budgetAmt, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0;
            return new BudgetResponse(null, catId, cat.getName(), cat.getColor(),
                    budgetAmt, spent, pct, yearMonth);
        }).toList();
    }

    @Transactional
    public BudgetResponse upsert(UUID userId, BudgetRequest req) {
        User user = userRepository.getReferenceById(userId);
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + req.categoryId()));

        Budget budget = budgetRepository
                .findByUserIdAndCategoryIdAndYearMonth(userId, req.categoryId(), req.yearMonth())
                .orElseGet(() -> Budget.builder().user(user).category(category).yearMonth(req.yearMonth()).build());

        budget.setAmount(req.amount());
        budget = budgetRepository.save(budget);

        return new BudgetResponse(budget.getId(), category.getId(), category.getName(),
                category.getColor(), budget.getAmount(), BigDecimal.ZERO, 0.0, req.yearMonth());
    }

    @Transactional
    public void delete(Integer budgetId, UUID userId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found: " + budgetId));
        if (!budget.getUser().getId().equals(userId))
            throw new ResourceNotFoundException("Budget not found: " + budgetId);
        budgetRepository.delete(budget);
    }
}
