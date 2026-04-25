package com.expensetracker.repository;

import com.expensetracker.domain.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, Integer> {

    List<Budget> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    Optional<Budget> findByUserIdAndCategoryIdAndYearMonth(UUID userId, Integer categoryId, String yearMonth);

    @Query("""
        SELECT b.category.id, b.amount,
               COALESCE(SUM(t.amount), 0) AS spent
        FROM Budget b
        LEFT JOIN Transaction t
          ON t.user.id = b.user.id
          AND t.category.id = b.category.id
          AND t.transactionType = com.expensetracker.domain.enums.TransactionType.DEBIT
          AND FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM') = b.yearMonth
        WHERE b.user.id = :userId
          AND b.yearMonth = :yearMonth
        GROUP BY b.category.id, b.amount
    """)
    List<Object[]> getBudgetProgress(@Param("userId") UUID userId, @Param("yearMonth") String yearMonth);
}
