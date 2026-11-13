package org.dr.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.User;
import org.dr.repository.DeviceSessionRepository;
import org.dr.repository.UserRepository;
import org.dr.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DeviceSessionRepository deviceSessionRepository;

    /** WebSocket/STOMP handshake endpoint. Auth happens on the STOMP CONNECT frame instead. */
    private static final String WS_HANDSHAKE_PATH = "/chat";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // The WebSocket handshake carries no usable Authorization header in most
        // browsers, and /chat is a public endpoint anyway. Authentication for
        // STOMP happens in JwtChannelInterceptor on the CONNECT frame, so
        // running the JWT check here only produces misleading warnings.
        if (request.getRequestURI() != null && request.getRequestURI().startsWith(WS_HANDSHAKE_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractTokenFromRequest(request);

        if (token == null) {
            // Normal for public endpoints; for protected ones this means the app sent no token.
            log.debug("JWT: no Bearer token on {} {}", request.getMethod(), request.getRequestURI());
        } else if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            if (!jwtService.isTokenValid(token)) {
                log.warn("JWT rejected on {}: invalid or expired token", request.getRequestURI());
                return;
            }

            // FIX: Ensure it is specifically an ACCESS token, not a refresh token!
            if (!jwtService.isAccessToken(token)) {
                log.warn("JWT rejected on {}: provided token is not an access token", request.getRequestURI());
                return;
            }

            String userId = jwtService.extractUserId(token);
            String sessionId = jwtService.extractSessionId(token);

            // ... (rest of your existing authentication code)

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("JWT rejected: user {} not found", userId);
                return;
            }

            var session = deviceSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                log.warn("JWT rejected: session {} not found", sessionId);
                return;
            }
            if (session.isRevoked()) {
                log.warn("JWT rejected: session {} is revoked", sessionId);
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (!session.getExpiresAt().isAfter(now)) {
                log.warn("JWT rejected: session {} expired at {}", sessionId, session.getExpiresAt());
                return;
            }

            // Throttle the DB write: at most once per minute per session.
            if (session.getLastActiveAt() == null
                    || session.getLastActiveAt().isBefore(now.minusMinutes(1))) {
                session.setLastActiveAt(now);
                deviceSessionRepository.save(session);
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.toString());
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}