package com.banking.config;

import com.banking.exception.ForbiddenException;
import com.banking.exception.UnauthorizedException;
import com.banking.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public JwtAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. Extract and validate Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authentication required: Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        Long tokenCustomerId;
        try {
            tokenCustomerId = jwtService.extractCustomerId(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired authentication token: " + e.getMessage());
        }

        // 2. Strict Ownership Verification:
        // If the path includes {customerId}, verify that the token's customer ID matches the path customer ID
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );

        if (uriVariables != null && uriVariables.containsKey("customerId")) {
            try {
                Long pathCustomerId = Long.parseLong(uriVariables.get("customerId"));
                if (!tokenCustomerId.equals(pathCustomerId)) {
                    throw new ForbiddenException("Access denied: You do not have permission to access another customer's resources");
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Store authenticated customer ID on request attribute for downstream controller use if needed
        request.setAttribute("authenticatedCustomerId", tokenCustomerId);
        return true;
    }
}
