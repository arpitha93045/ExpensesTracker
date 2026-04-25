package com.expensetracker.controller;

import com.expensetracker.domain.enums.TransactionType;
import com.expensetracker.dto.request.TransactionUpdateRequest;
import com.expensetracker.dto.response.PagedResponse;
import com.expensetracker.dto.response.TransactionResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management APIs")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List transactions with optional search, category, type, date filters and pagination")
    public PagedResponse<TransactionResponse> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = resolveUserId(userDetails);
        String searchTrim = (search != null && search.isBlank()) ? null : search;
        Page<TransactionResponse> result = transactionService.getTransactions(
                userId, from, to, searchTrim, categoryId, type,
                PageRequest.of(page, Math.min(size, 100)));
        return toPagedResponse(result);
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export transactions as CSV with same filters as list endpoint")
    public void exportCsv(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) TransactionType type,
            HttpServletResponse response) throws IOException {

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions.csv\"");

        UUID userId = resolveUserId(userDetails);
        String searchTrim = (search != null && search.isBlank()) ? null : search;
        transactionService.exportCsv(userId, from, to, searchTrim, categoryId, type, response.getWriter());
    }

    @PatchMapping("/{id}/category")
    @Operation(summary = "Update the category of a transaction")
    public TransactionResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return transactionService.updateCategory(id, request.categoryId(), request.categorizationNote(),
                resolveUserId(userDetails));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a transaction")
    public void deleteTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        transactionService.deleteTransaction(id, resolveUserId(userDetails));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
