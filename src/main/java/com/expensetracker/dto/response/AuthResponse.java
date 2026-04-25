package com.expensetracker.dto.response;

public record AuthResponse(
        String userId,
        String email,
        String fullName,
        String role
) {}
