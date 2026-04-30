package com.expensetracker.controller;

import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.request.LoginRequest;
import com.expensetracker.dto.request.MfaVerifyRequest;
import com.expensetracker.dto.request.RegisterRequest;
import com.expensetracker.dto.request.TotpConfirmRequest;
import com.expensetracker.dto.response.AuthResponse;
import com.expensetracker.dto.response.TotpSetupResponse;
import com.expensetracker.dto.response.TwoFactorStatusResponse;
import com.expensetracker.exception.AuthException;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT-based authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        return authService.register(request, response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT cookies, or get TOTP challenge")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletResponse response) {
        AuthResponse result = authService.login(request, userAgent, response);
        if (Boolean.TRUE.equals(result.mfaRequired())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh cookie")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and clear JWT cookies")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    // ── MFA verification (public — no JWT yet) ──────────────────────────────

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete TOTP challenge and receive JWT cookies")
    public AuthResponse verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response) {
        User user = twoFactorService.verifyChallenge(request.challengeId(), request.code());
        authService.issueTokenCookies(user, null, response);
        return AuthResponse.authenticated(
                user.getId().toString(), user.getEmail(), user.getFullName(), user.getRole());
    }

    // ── 2FA management (requires authenticated JWT) ─────────────────────────

    @GetMapping("/2fa/status")
    @Operation(summary = "Get current 2FA status for the authenticated user")
    public TwoFactorStatusResponse get2faStatus(@AuthenticationPrincipal UserDetails principal) {
        User user = resolveUser(principal);
        return new TwoFactorStatusResponse(user.isTotpEnabled());
    }

    @PostMapping("/2fa/totp/setup")
    @Operation(summary = "Start TOTP setup — returns secret and QR code")
    public TotpSetupResponse setupTotp(@AuthenticationPrincipal UserDetails principal) {
        return twoFactorService.setupTotp(resolveUser(principal));
    }

    @PostMapping("/2fa/totp/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm TOTP setup with a valid code to enable it")
    public void confirmTotp(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TotpConfirmRequest request) {
        twoFactorService.confirmTotpSetup(resolveUser(principal), request.code());
    }

    @DeleteMapping("/2fa/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disable TOTP for the authenticated user")
    public void disableTotp(@AuthenticationPrincipal UserDetails principal) {
        twoFactorService.disableTotp(resolveUser(principal));
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new AuthException("User not found"));
    }
}
