package com.expensetracker.controller;

import com.expensetracker.domain.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile and preferences")
public class UserProfileController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 255) String email
    ) {}

    record UpdatePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}

    record UpdatePreferencesRequest(
            Boolean notificationsEnabled,
            @Size(max = 3) String defaultCurrency
    ) {}

    record UserProfileResponse(
            String userId,
            String email,
            String fullName,
            String defaultCurrency,
            boolean notificationsEnabled,
            String createdAt
    ) {}

    @GetMapping
    @Operation(summary = "Get current user profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return toResponse(resolveUser(userDetails));
    }

    @PutMapping
    @Operation(summary = "Update name and email")
    public UserProfileResponse updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        String newEmail = request.email().toLowerCase().trim();

        if (!user.getEmail().equals(newEmail)) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(newEmail);
            refreshTokenRepository.revokeAllByUserId(user.getId());
        }
        user.setFullName(request.fullName().trim());
        return toResponse(userRepository.save(user));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password")
    public void updatePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification and currency preferences")
    public UserProfileResponse updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveUser(userDetails);
        if (request.notificationsEnabled() != null) {
            user.setNotificationsEnabled(request.notificationsEnabled());
        }
        if (request.defaultCurrency() != null && !request.defaultCurrency().isBlank()) {
            user.setDefaultCurrency(request.defaultCurrency().toUpperCase().trim());
        }
        return toResponse(userRepository.save(user));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getDefaultCurrency(),
                user.isNotificationsEnabled(),
                user.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
    }
}
