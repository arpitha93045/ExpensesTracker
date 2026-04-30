package com.expensetracker.dto.response;

public record TotpSetupResponse(String secret, String qrDataUri) {}
