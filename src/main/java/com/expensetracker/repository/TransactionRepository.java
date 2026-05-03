package com.expensetracker.repository;

import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    List<Transaction> findByUserIdAndAiCategorizedFalse(UUID userId);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
          AND t.uploadJob.id = :jobId
    """)
    List<Transaction> findByUserIdAndUploadJobId(
            @Param("userId") UUID userId,
            @Param("jobId") UUID jobId);

    // ── Search with optional filters ────────────────────────────
    @Query(value = """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.user_id = :userId
          AND (:from IS NULL OR t.transaction_date >= CAST(:from AS date))
          AND (:to IS NULL OR t.transaction_date <= CAST(:to AS date))
          AND (
               LOWER(t.description) LIKE LOWER(CONCAT('%', COALESCE(:search, t.description), '%'))
               OR (t.merchant IS NOT NULL AND LOWER(t.merchant) LIKE LOWER(CONCAT('%', COALESCE(:search, t.merchant), '%'))))
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:txType IS NULL OR t.transaction_type = :txType)
        ORDER BY t.transaction_date DESC, t.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(t.id) FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.user_id = :userId
          AND (:from IS NULL OR t.transaction_date >= CAST(:from AS date))
          AND (:to IS NULL OR t.transaction_date <= CAST(:to AS date))
          AND (
               LOWER(t.description) LIKE LOWER(CONCAT('%', COALESCE(:search, t.description), '%'))
               OR (t.merchant IS NOT NULL AND LOWER(t.merchant) LIKE LOWER(CONCAT('%', COALESCE(:search, t.merchant), '%'))))
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:txType IS NULL OR t.transaction_type = :txType)
        """,
        nativeQuery = true)
    Page<Transaction> search(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("search") String search,
            @Param("categoryId") Integer categoryId,
            @Param("txType") String txType,
            Pageable pageable);

    // ── Export all (no pagination) with same filters ────────────
    @Query(value = """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.user_id = :userId
          AND (:from IS NULL OR t.transaction_date >= CAST(:from AS date))
          AND (:to IS NULL OR t.transaction_date <= CAST(:to AS date))
          AND (
               LOWER(t.description) LIKE LOWER(CONCAT('%', COALESCE(:search, t.description), '%'))
               OR (t.merchant IS NOT NULL AND LOWER(t.merchant) LIKE LOWER(CONCAT('%', COALESCE(:search, t.merchant), '%'))))
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:txType IS NULL OR t.transaction_type = :txType)
        ORDER BY t.transaction_date DESC, t.created_at DESC
        """,
        nativeQuery = true)
    List<Transaction> searchAll(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("search") String search,
            @Param("categoryId") Integer categoryId,
            @Param("txType") String txType);

    // ── Tax report: all debits in a date range ───────────────────
    @Query(value = """
        SELECT t.* FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_date >= CAST(:from AS date)
          AND t.transaction_date <= CAST(:to AS date)
          AND t.transaction_type = 'DEBIT'
        ORDER BY t.transaction_date ASC
        """, nativeQuery = true)
    List<Transaction> findDebitsInRange(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ── Duplicate detection ──────────────────────────────────────
    @Query(value = """
        SELECT CONCAT(t.transaction_date, '|', t.amount, '|', t.transaction_type, '|', t.description)
        FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_date = ANY(CAST(:dates AS date[]))
        """, nativeQuery = true)
    List<String> findExistingKeys(
            @Param("userId") UUID userId,
            @Param("dates") String dates);

    // ── Bill predictor ──────────────────────────────────────────
    @Query(value = """
        SELECT t.merchant, t.amount, t.transaction_date
        FROM transactions t
        WHERE t.user_id = :userId
          AND t.merchant IS NOT NULL
          AND t.transaction_type = 'DEBIT'
          AND t.transaction_date >= :since
        ORDER BY t.merchant ASC, t.transaction_date ASC
        """, nativeQuery = true)
    List<Object[]> getMerchantTransactionHistory(
            @Param("userId") UUID userId,
            @Param("since") LocalDate since);

    // ── Lifestyle inflation ──────────────────────────────────────
    @Query(value = """
        SELECT c.name AS category_name,
               COALESCE(SUM(t.amount), 0) / 3.0 AS monthly_avg
        FROM transactions t
        JOIN categories c ON t.category_id = c.id
        WHERE t.user_id = :userId
          AND t.transaction_type = 'DEBIT'
          AND t.transaction_date BETWEEN :from AND :to
        GROUP BY c.name
        """, nativeQuery = true)
    List<Object[]> getCategoryMonthlyAvg(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(value = """
        SELECT DISTINCT t.merchant,
               MIN(t.transaction_date) AS first_seen,
               AVG(t.amount)           AS avg_amount
        FROM transactions t
        WHERE t.user_id = :userId
          AND t.merchant IS NOT NULL
          AND t.transaction_type = 'DEBIT'
          AND t.transaction_date BETWEEN :recentFrom AND :recentTo
          AND t.merchant NOT IN (
              SELECT DISTINCT merchant FROM transactions
              WHERE user_id = :userId
                AND merchant IS NOT NULL
                AND transaction_date BETWEEN :priorFrom AND :priorTo
          )
        GROUP BY t.merchant
        ORDER BY avg_amount DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> getNewMerchants(
            @Param("userId") UUID userId,
            @Param("recentFrom") LocalDate recentFrom,
            @Param("recentTo") LocalDate recentTo,
            @Param("priorFrom") LocalDate priorFrom,
            @Param("priorTo") LocalDate priorTo);

    // ── What-if simulator ────────────────────────────────────────
    @Query(value = """
        SELECT COALESCE(SUM(t.amount), 0) /
               NULLIF(COUNT(DISTINCT TO_CHAR(t.transaction_date, 'YYYY-MM')), 0)
        FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_type = 'DEBIT'
          AND t.merchant IS NOT NULL
          AND LOWER(t.merchant) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%'))
          AND t.transaction_date >= :since
        """, nativeQuery = true)
    BigDecimal getMonthlyAvgByMerchant(
            @Param("userId") UUID userId,
            @Param("term") String term,
            @Param("since") LocalDate since);

    @Query(value = """
        SELECT COALESCE(SUM(t.amount), 0) /
               NULLIF(COUNT(DISTINCT TO_CHAR(t.transaction_date, 'YYYY-MM')), 0)
        FROM transactions t
        JOIN categories c ON t.category_id = c.id
        WHERE t.user_id = :userId
          AND t.transaction_type = 'DEBIT'
          AND LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%'))
          AND t.transaction_date >= :since
        """, nativeQuery = true)
    BigDecimal getMonthlyAvgByCategory(
            @Param("userId") UUID userId,
            @Param("term") String term,
            @Param("since") LocalDate since);

    // ── Cash flow calendar ───────────────────────────────────────
    @Query(value = """
        SELECT
            t.transaction_date AS date,
            SUM(CASE WHEN t.transaction_type = 'DEBIT'  THEN t.amount ELSE 0 END) AS total_debit,
            SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE 0 END) AS total_credit,
            SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END) AS net_flow,
            COUNT(*) AS transaction_count
        FROM transactions t
        WHERE t.user_id = :userId
          AND t.transaction_date BETWEEN :from AND :to
        GROUP BY t.transaction_date
        ORDER BY t.transaction_date ASC
        """, nativeQuery = true)
    List<Object[]> getDailyFlow(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.category
        WHERE t.user.id = :userId
          AND t.transactionDate = :date
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByUserIdAndDate(
            @Param("userId") UUID userId,
            @Param("date") LocalDate date);

    // ── Analytics ────────────────────────────────────────────────
    @Query("""
        SELECT c.name AS category,
               SUM(t.amount) AS total,
               COUNT(t) AS txnCount
        FROM Transaction t
        JOIN t.category c
        WHERE t.user.id = :userId
          AND t.transactionType = com.expensetracker.domain.enums.TransactionType.DEBIT
          AND t.transactionDate BETWEEN :from AND :to
        GROUP BY c.name, c.color, c.icon
        ORDER BY total DESC
    """)
    List<Object[]> getCategoryBreakdown(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM') AS month,
               SUM(CASE WHEN t.transactionType = com.expensetracker.domain.enums.TransactionType.DEBIT  THEN t.amount ELSE 0 END) AS expenses,
               SUM(CASE WHEN t.transactionType = com.expensetracker.domain.enums.TransactionType.CREDIT THEN t.amount ELSE 0 END) AS income
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :since
        GROUP BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        ORDER BY month ASC
    """)
    List<Object[]> getMonthlyTrend(
            @Param("userId") UUID userId,
            @Param("since") LocalDate since);

    @Query("""
        SELECT t.merchant, COUNT(t), SUM(t.amount)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionType = com.expensetracker.domain.enums.TransactionType.DEBIT
          AND t.transactionDate BETWEEN :from AND :to
          AND t.merchant IS NOT NULL
        GROUP BY t.merchant
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> getTopMerchants(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
