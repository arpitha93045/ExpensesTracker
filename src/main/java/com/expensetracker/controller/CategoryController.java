package com.expensetracker.controller;

import com.expensetracker.domain.entity.Category;
import com.expensetracker.domain.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Expense categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 10) String icon,
        @Size(max = 20) String color
    ) {}

    @GetMapping
    @Operation(summary = "List all available categories (global + user-custom)")
    public List<Map<String, Object>> getCategories(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return categoryRepository.findAllForUser(userId).stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "name", c.getName(),
                        "icon", c.getIcon() != null ? c.getIcon() : "",
                        "color", c.getColor() != null ? c.getColor() : "#BDC3C7",
                        "custom", c.getUser() != null
                )).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a custom category for the current user")
    public Map<String, Object> createCategory(
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean exists = categoryRepository.findAllForUser(userId).stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(request.name()));
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");

        Category category = Category.builder()
                .name(request.name().trim())
                .icon(request.icon() != null ? request.icon().trim() : "🏷️")
                .color(request.color() != null ? request.color() : "#BDC3C7")
                .user(user)
                .build();
        Category saved = categoryRepository.save(category);
        return toMap(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a custom category (user-owned only)")
    public Map<String, Object> updateCategory(
            @PathVariable Integer id,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        if (category.getUser() == null || !category.getUser().getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit global or another user's category");

        category.setName(request.name().trim());
        category.setIcon(request.icon() != null ? request.icon().trim() : category.getIcon());
        category.setColor(request.color() != null ? request.color() : category.getColor());
        return toMap(categoryRepository.save(category));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a custom category (user-owned only)")
    public void deleteCategory(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        if (category.getUser() == null || !category.getUser().getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete a global category");

        categoryRepository.delete(category);
    }

    private Map<String, Object> toMap(Category c) {
        return Map.of(
                "id", c.getId(),
                "name", c.getName(),
                "icon", c.getIcon() != null ? c.getIcon() : "",
                "color", c.getColor() != null ? c.getColor() : "#BDC3C7",
                "custom", c.getUser() != null
        );
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
