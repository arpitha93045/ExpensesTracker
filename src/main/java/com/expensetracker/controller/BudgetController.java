package com.expensetracker.controller;

import com.expensetracker.dto.request.BudgetRequest;
import com.expensetracker.dto.response.BudgetResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Monthly category budget APIs")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get budgets with spending progress for a month (format: YYYY-MM)")
    public List<BudgetResponse> getBudgets(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String yearMonth) {
        return budgetService.getBudgetsWithProgress(resolveUserId(userDetails), yearMonth);
    }

    @PutMapping
    @Operation(summary = "Create or update a budget for a category/month")
    public BudgetResponse upsert(
            @Valid @RequestBody BudgetRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return budgetService.upsert(resolveUserId(userDetails), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a budget")
    public void delete(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        budgetService.delete(id, resolveUserId(userDetails));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
