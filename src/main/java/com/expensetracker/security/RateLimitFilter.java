package com.expensetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.login.capacity:10}")
    private int loginCapacity;

    @Value("${app.rate-limit.login.refill-minutes:15}")
    private int loginRefillMinutes;

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equals(method) && path.endsWith("/auth/login")) {
            String ip = extractIp(request);
            Bucket bucket = loginBuckets.computeIfAbsent(ip, k -> newLoginBucket());
            if (!bucket.tryConsume(1)) {
                long waitSeconds = bucket.getAvailableTokens() == 0
                        ? Duration.ofMinutes(loginRefillMinutes).toSeconds()
                        : 1;
                rejectWithTooManyRequests(response, waitSeconds,
                        "Too many login attempts. Please try again in " + loginRefillMinutes + " minutes.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, long retryAfterSeconds,
                                           String detail) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        Map<String, Object> body = Map.of(
                "status", 429,
                "title", "Too Many Requests",
                "detail", detail,
                "timestamp", Instant.now().toString()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(loginCapacity,
                        Refill.intervally(loginCapacity, Duration.ofMinutes(loginRefillMinutes))))
                .build();
    }
}
