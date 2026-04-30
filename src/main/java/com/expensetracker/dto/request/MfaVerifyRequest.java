package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank String challengeId,
        @NotBlank @Size(min = 6, max = 8) String code
) {}
