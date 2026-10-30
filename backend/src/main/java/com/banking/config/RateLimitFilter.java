package com.banking.config;

import com.banking.systemdesign.ratelimiter.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Servlet filter that applies rate limiting to sensitive banking API endpoints.
 * Runs before Spring MVC interceptors so rate-limited requests are rejected
 * before authentication or business logic processing.
 *
 * Rate limit key strategy:
 * - Authenticated endpoints: uses "Bearer {token}" hash or customer ID from path
 * - Unauthenticated (login/register): uses client IP address
 *
 * Does NOT rate-limit: static assets, health checks, actuator endpoints.
 */
import com.banking.service.JwtService;
import com.banking.service.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final MetricsService metricsService;
    private final JwtService jwtService;

    @Value("${ratelimiter.retry-after-seconds:15}")
    private int retryAfterSeconds;

    /** Endpoints that should be rate-limited */
    private static final Set<String> RATE_LIMITED_PREFIXES = Set.of(
            "/api/customers/login",
            "/api/customers/register",
            "/api/transfers",
            "/api/bills/pay",
            "/api/admin/login"
    );

    /** Endpoints that should NOT be rate-limited */
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api/health",
            "/actuator"
    );

    @Autowired
    public RateLimitFilter(RateLimiter rateLimiter, MetricsService metricsService, JwtService jwtService) {
        this.rateLimiter = rateLimiter;
        this.metricsService = metricsService;
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip non-API requests, OPTIONS (CORS preflight), and excluded endpoints
        if ("OPTIONS".equalsIgnoreCase(method) ||
            !path.startsWith("/api") ||
            isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Only rate-limit matching endpoints
        if (!shouldRateLimit(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Build a rate-limit key based on endpoint and client identity
        String rateLimitKey = buildRateLimitKey(request, path);

        if (!rateLimiter.allowRequest(rateLimitKey)) {
            if (metricsService != null) {
                metricsService.recordRateLimitRejection();
            }
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("timestamp", Instant.now().toString());
            errorBody.put("status", 429);
            errorBody.put("error", "Too many requests. Please try again later.");
            errorBody.put("retryAfter", retryAfterSeconds);

            new ObjectMapper().writeValue(response.getWriter(), errorBody);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean shouldRateLimit(String path) {
        // MPIN verification attempts must always be rate-limited
        if (path.contains("/verify-mpin")) {
            return true;
        }

        // Transactions refresh rate-limited under its own isolated txns bucket
        if (path.contains("/transactions")) {
            return true;
        }

        // Beneficiary addition/modifications
        if (path.contains("/beneficiaries")) {
            return true;
        }

        for (String prefix : RATE_LIMITED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(String path) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a rate-limit key. For authenticated endpoints, extracts a validated customer
     * identifier from the JWT or path. For unauthenticated endpoints (login, register),
     * uses the client IP. Never includes secrets, raw tokens, or MPINs in the key.
     */
    private String buildRateLimitKey(HttpServletRequest request, String path) {
        // For login/register: key by IP to prevent brute force
        if (path.contains("/login") || path.contains("/register")) {
            String ip = getClientIp(request);
            return "auth:" + ip;
        }

        // For authenticated requests: extract customerId from verified JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtService != null) {
            try {
                String token = authHeader.substring(7).trim();
                Long customerId = jwtService.extractCustomerId(token);
                if (customerId != null) {
                    return "customer:" + customerId + ":" + getEndpointCategory(path);
                }
            } catch (Exception ignored) {
                // If token is invalid, fall through to path or IP
            }
        }

        // For authenticated endpoints: extract customerId from path if present
        // Pattern: /api/customers/{customerId}/...
        if (path.startsWith("/api/customers/")) {
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                try {
                    Long.parseLong(parts[3]);
                    return "customer:" + parts[3] + ":" + getEndpointCategory(path);
                } catch (NumberFormatException ignored) {}
            }
        }

        // For transfers and bill payments without JWT: key by IP as fallback
        String ip = getClientIp(request);
        return "api:" + ip + ":" + getEndpointCategory(path);
    }

    private String getEndpointCategory(String path) {
        if (path.contains("/verify-mpin")) return "mpin";
        if (path.contains("/transactions")) return "txns";
        if (path.contains("/beneficiaries")) return "bene";
        if (path.contains("/transfers")) return "transfer";
        if (path.contains("/bills")) return "bills";
        if (path.contains("/accounts")) return "accounts";
        return "general";
    }

    private String getClientIp(HttpServletRequest request) {
        // Use request.getRemoteAddr() to ensure client-supplied X-Forwarded-For headers cannot bypass rate limits
        return request.getRemoteAddr();
    }
}
