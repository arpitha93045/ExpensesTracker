package com.expensetracker.service;

import com.expensetracker.domain.entity.RefreshToken;
import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.request.LoginRequest;
import com.expensetracker.dto.request.RegisterRequest;
import com.expensetracker.dto.response.AuthResponse;
import com.expensetracker.exception.AuthException;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("Email already registered");
        }
        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .build();
        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());
        issueTokenCookies(user, null, response);
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo, HttpServletResponse response) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("User not found"));
        issueTokenCookies(user, deviceInfo, response);
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractCookie(request, "refresh_token");
        if (rawToken == null) throw new AuthException("Refresh token not found");

        String tokenHash = sha256(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        if (stored.isRevoked() || stored.isExpired()) {
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw new AuthException("Refresh token expired or revoked. Please login again.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        issueTokenCookies(user, stored.getDeviceInfo(), response);
        return toAuthResponse(user);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractCookie(request, "refresh_token");
        if (rawToken != null) {
            String tokenHash = sha256(rawToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }
        clearCookies(response);
    }

    private void issueTokenCookies(User user, String deviceInfo, HttpServletResponse response) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);

        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefreshToken))
                .deviceInfo(deviceInfo)
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenExpiryDays))
                .build();
        refreshTokenRepository.save(refreshToken);

        response.addCookie(buildCookie("access_token", accessToken, 15 * 60));
        response.addCookie(buildCookie("refresh_token", rawRefreshToken, (int) (refreshTokenExpiryDays * 86400)));
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set true in production behind HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
    }

    private void clearCookies(HttpServletResponse response) {
        response.addCookie(buildCookie("access_token", "", 0));
        response.addCookie(buildCookie("refresh_token", "", 0));
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(user.getId().toString(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
