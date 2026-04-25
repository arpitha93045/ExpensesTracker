package com.expensetracker.service;

import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.Transaction;
import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.dto.response.TransactionResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public Page<TransactionResponse> getTransactions(
            UUID userId, LocalDate from, LocalDate to,
            String search, Integer categoryId, TransactionType txType,
            Pageable pageable) {
        return transactionRepository
                .search(userId, from, to, search, categoryId, txType == null ? null : txType.name(), pageable)
                .map(this::toResponse);
    }

    public void exportCsv(UUID userId, LocalDate from, LocalDate to,
                          String search, Integer categoryId, TransactionType txType,
                          PrintWriter writer) {
        List<Transaction> transactions = transactionRepository.searchAll(
                userId, from, to, search, categoryId, txType == null ? null : txType.name());

        writer.println("Date,Description,Merchant,Category,Type,Amount,Currency");
        for (Transaction tx : transactions) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    tx.getTransactionDate(),
                    escape(tx.getDescription()),
                    tx.getMerchant() != null ? escape(tx.getMerchant()) : "",
                    tx.getCategory() != null ? escape(tx.getCategory().getName()) : "Uncategorized",
                    tx.getTransactionType(),
                    tx.getAmount(),
                    tx.getCurrency());
        }
        writer.flush();
    }

    @Transactional
    public TransactionResponse updateCategory(UUID transactionId, Integer categoryId, String note, UUID userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        tx.setCategory(category);
        tx.setCategorizationNote(note);
        tx.setAiCategorized(false);
        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID userId) {
        Transaction tx = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        transactionRepository.delete(tx);
    }

    public TransactionResponse toResponse(Transaction tx) {
        TransactionResponse.CategoryInfo catInfo = null;
        if (tx.getCategory() != null) {
            Category c = tx.getCategory();
            catInfo = new TransactionResponse.CategoryInfo(c.getId(), c.getName(), c.getIcon(), c.getColor());
        }
        return new TransactionResponse(
                tx.getId(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionDate(),
                tx.getTransactionType(),
                tx.getMerchant(),
                catInfo,
                tx.isAiCategorized(),
                tx.getAiConfidence(),
                tx.getCategorizationNote(),
                tx.getCreatedAt()
        );
    }

    private String escape(String value) {
        return value.replace("\"", "\"\"");
    }
}
