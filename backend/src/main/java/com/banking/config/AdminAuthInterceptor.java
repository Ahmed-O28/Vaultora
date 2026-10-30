package com.banking.config;

import com.banking.exception.ForbiddenException;
import com.banking.exception.UnauthorizedException;
import com.banking.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public AdminAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. Extract and validate Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Admin authentication required: Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        String role;
        Long adminId;
        try {
            role = jwtService.extractRole(token);
            adminId = jwtService.extractCustomerId(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired authentication token: " + e.getMessage());
        }

        // 2. Strict Role Verification
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Access denied: Admin role required to access this resource");
        }

        request.setAttribute("authenticatedAdminId", adminId);
        request.setAttribute("authenticatedRole", role);
        return true;
    }
}
