package com.expensetracker.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String userId,
        String email,
        String fullName,
        String role,
        Boolean mfaRequired,
        String challengeId,
        String method
) {
    public static AuthResponse authenticated(String userId, String email, String fullName, String role) {
        return new AuthResponse(userId, email, fullName, role, null, null, null);
    }

    public static AuthResponse mfaChallenge(String challengeId, String method) {
        return new AuthResponse(null, null, null, null, true, challengeId, method);
    }
}
