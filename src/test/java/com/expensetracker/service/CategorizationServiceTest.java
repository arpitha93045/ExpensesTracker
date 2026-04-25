package com.expensetracker.service;

import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.entity.User;
import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategorizationServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks private CategorizationService categorizationService;

    private List<Category> mockCategories;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockCategories = List.of(
                buildCategory(1, "Food & Dining"),
                buildCategory(2, "Transportation"),
                buildCategory(3, "Shopping"),
                buildCategory(14, "Other")
        );
        when(categoryRepository.findAllForUser(any())).thenReturn(mockCategories);
    }

    @Test
    void categorizeTransactions_starbucksKeyword_matchesFoodDining() {
        Transaction tx = buildTransaction("STARBUCKS #1234");

        categorizationService.categorizeTransactions(List.of(tx), userId);

        assertThat(tx.getCategory()).isNotNull();
        assertThat(tx.getCategory().getName()).isEqualTo("Food & Dining");
        assertThat(tx.isAiCategorized()).isFalse();
        verify(transactionRepository).saveAll(any());
    }

    @Test
    void categorizeTransactions_uberKeyword_matchesTransportation() {
        Transaction tx = buildTransaction("UBER TRIP");

        categorizationService.categorizeTransactions(List.of(tx), userId);

        assertThat(tx.getCategory().getName()).isEqualTo("Transportation");
    }

    @Test
    void categorizeTransactions_unknownDescription_fallsBackToOther() {
        Transaction tx = buildTransaction("XZQR1234PAYMENT");

        categorizationService.categorizeTransactions(List.of(tx), userId);

        assertThat(tx.getCategory().getName()).isEqualTo("Other");
    }

    @Test
    void categorizeTransactions_emptyList_doesNothing() {
        categorizationService.categorizeTransactions(List.of(), userId);
        verify(transactionRepository, never()).saveAll(any());
    }

    private Transaction buildTransaction(String description) {
        User user = new User();
        user.setId(userId);
        return Transaction.builder()
                .id(UUID.randomUUID())
                .user(user)
                .description(description)
                .amount(BigDecimal.TEN)
                .transactionDate(LocalDate.now())
                .transactionType(TransactionType.DEBIT)
                .build();
    }

    private Category buildCategory(int id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }
}
