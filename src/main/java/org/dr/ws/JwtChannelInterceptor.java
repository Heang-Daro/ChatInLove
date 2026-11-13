package org.dr.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import org.dr.model.User;
import org.dr.repository.DeviceSessionRepository;
import org.dr.repository.UserRepository;
import org.dr.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DeviceSessionRepository deviceSessionRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                throw new MessagingException("Unauthorized subscription");
            }
            // TODO: check conversation membership for /topic/room/**
        }
        return message;
    }

    /**
     * Authenticate the STOMP CONNECT frame.
     *
     * <p>Browsers cannot attach an Authorization header to the WebSocket
     * handshake, so the token is supplied on the CONNECT frame itself.
     */
    private void authenticateConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing Authorization header");
        }

        String token = header.substring(7).trim();

        if (!jwtService.isTokenValid(token)) {
            // The client cannot tell "expired" from "garbage", so tell it explicitly:
            // an expired access token should be refreshed, not re-authenticated.
            throw new MessagingException(jwtService.isTokenExpired(token)
                    ? "Access token expired - refresh required"
                    : "Invalid token - login required");
        }

        // A refresh token must never be usable as a bearer token.
        if (!jwtService.isAccessToken(token)) {
            throw new MessagingException("Invalid token - login required");
        }

        String userId = jwtService.extractUserId(token);
        String sessionId = jwtService.extractSessionId(token);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new MessagingException("Invalid token - login required");
        }

        // The device session must still be alive; otherwise a revoked or
        // expired session could keep using the socket.
        var session = deviceSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isRevoked()) {
            throw new MessagingException("Session revoked - login required");
        }
        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new MessagingException("Session expired - login required");
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        log.debug("User {} authenticated via WebSocket", userId);
    }
}
