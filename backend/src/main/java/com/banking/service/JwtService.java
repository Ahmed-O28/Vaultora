package com.banking.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * Generates a signed JWT token containing the customer ID as subject and email in claims.
     */
    public String generateToken(Long customerId, String email) {
        return generateToken(customerId, email, "CUSTOMER");
    }

    /**
     * Generates a signed JWT token with custom role (e.g., ADMIN, CUSTOMER).
     */
    public String generateToken(Long id, String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("email", email)
                .claim("role", role != null ? role : "CUSTOMER")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Validates signature and expiration, returning claims if valid.
     * Throws JwtException if expired, malformed, or tampered with.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractCustomerId(String token) {
        Claims claims = validateAndExtractClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = validateAndExtractClaims(token);
        return claims.get("email", String.class);
    }

    public String extractRole(String token) {
        Claims claims = validateAndExtractClaims(token);
        String role = claims.get("role", String.class);
        return role != null ? role : "CUSTOMER";
    }
}
