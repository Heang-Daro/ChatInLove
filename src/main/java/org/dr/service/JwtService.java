package org.dr.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    // Secret must be at least 32 characters (256 bits) for HS256
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Generate access token for the user, bound to a device session */
    public String generateAccessToken(String userId, String sessionId) {
        return createToken(userId, sessionId, TYPE_ACCESS,
                jwtProperties.getExpirationMinutes() * 60L);
    }

    /** Generate refresh token for the device session */
    public String generateRefreshToken(String userId, String sessionId) {
        return createToken(userId, sessionId, TYPE_REFRESH,
                jwtProperties.getRefreshExpirationDays() * 24 * 60 * 60L);
    }

    private String createToken(String subject, String sessionId, String type, long expirationSeconds) {
        Instant now = Instant.now();

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_SESSION_ID, sessionId);
        claims.put(CLAIM_TYPE, type);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(getSigningKey()) // algorithm (HS256/384/512) is chosen from the key size
                .compact();
    }

    /** Extract user ID (subject) from token */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extract device session ID from token */
    public String extractSessionId(String token) {
        return extractAllClaims(token).get(CLAIM_SESSION_ID, String.class);
    }

    /** True only for access tokens, so a refresh token can't be used as a bearer token */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractAllClaims(token).get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractAllClaims(token).get(CLAIM_TYPE, String.class));
    }

    /** Signature is correct and token is not expired */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token has expired");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token is invalid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * True only when the token is a well-formed token whose expiry has passed.
     *
     * <p>A token that cannot be parsed at all (bad signature, malformed) is
     * <em>not</em> reported as expired - it is simply invalid. This lets
     * callers distinguish "expired, refresh it" from "garbage, log in again".
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            // Signature was good, but the token is past its expiry.
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Cannot be parsed (bad signature / malformed) - invalid, not expired.
            return false;
        }
    }

    /** Parses and verifies the signature; throws JwtException if invalid or expired */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Remaining lifetime in seconds */
    public long getExpirationTimeInSeconds(String token) {
        long expirationTime = extractAllClaims(token).getExpiration().getTime();
        return (expirationTime - System.currentTimeMillis()) / 1000;
    }
}