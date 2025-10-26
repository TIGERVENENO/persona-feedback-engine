package ru.tigran.personafeedbackengine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service for JWT token generation and validation.
 * Uses HMAC-SHA256 for token signing.
 */
@Slf4j
@Service
public class JwtTokenProvider {

    private final SecretKey key;
    private final long tokenValidityInMilliseconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret-key}") String secretKey,
            @Value("${app.jwt.expiration-hours:24}") int expirationHours
    ) {
        // Generate key from secret string
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        // Convert hours to milliseconds
        this.tokenValidityInMilliseconds = expirationHours * 60L * 60 * 1000;
    }

    /**
     * Generates a JWT token for the given user ID.
     *
     * @param userId User ID to encode in token
     * @return JWT token string
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenValidityInMilliseconds);

        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.debug("Generated JWT token for user: {}", userId);
        return token;
    }

    /**
     * Validates JWT token and returns the user ID (subject).
     *
     * @param token JWT token to validate
     * @return User ID if token is valid
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.parseLong(claims.getSubject());
            log.debug("Validated JWT token for user: {}", userId);
            return userId;
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Validates JWT token without extracting claims.
     *
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the Bearer token from Authorization header.
     *
     * @param authHeader Authorization header value (e.g., "Bearer <token>")
     * @return Token string without "Bearer " prefix, or null if header is invalid
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
