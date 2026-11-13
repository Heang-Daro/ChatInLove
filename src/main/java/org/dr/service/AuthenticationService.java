package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.DeviceLocationRequest;
import org.dr.dto.request.LoginRequest;
import org.dr.dto.request.RefreshTokenRequest;
import org.dr.dto.request.RegisterRequest;
import org.dr.dto.response.AuthResponse;
import org.dr.exception.DuplicateResourceException;
import org.dr.exception.InvalidCredentialsException;
import org.dr.exception.ResourceNotFoundException;
import org.dr.model.DeviceSession;
import org.dr.model.User;
import org.dr.model.enums.DeviceType;
import org.dr.repository.DeviceSessionRepository;
import org.dr.repository.UserRepository;
import org.dr.util.TokenGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    private final UserRepository userRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final DeviceLocationResolver locationResolver;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Normalize email: trim and lowercase
        String normalizedEmail = request.email().trim().toLowerCase();

        // Check if email already exists
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email already registered: " + normalizedEmail);
        }

        // Create new user
        User user = User.builder()
                .email(normalizedEmail)
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        // Create device session and return auth response
        return createDeviceSessionAndReturnAuth(user, request.location());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Normalize email
        String normalizedEmail = request.email().trim().toLowerCase();

        // Find user by email
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getEmail());

        // Create device session and return auth response
        return createDeviceSessionAndReturnAuth(user, request.location());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // Verify refresh token is valid
        if (!jwtService.isTokenValid(request.refreshToken())) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        String userId = jwtService.extractUserId(request.refreshToken());
        String sessionId = jwtService.extractSessionId(request.refreshToken());

        // Get user and session
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DeviceSession session = deviceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        // Check if session is still valid and not revoked
        if (session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Session expired or revoked");
        }

        // Update last active time
        session.setLastActiveAt(LocalDateTime.now());
        deviceSessionRepository.save(session);

        // Generate new access token
        String accessToken = jwtService.generateAccessToken(user.getId(), sessionId);

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn((long) jwtService.getExpirationTimeInSeconds(accessToken))
                .build();
    }

    @Transactional
    public void logout(String userId, String sessionId) {
        DeviceSession session = deviceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        session.setRevoked(true);
        deviceSessionRepository.save(session);
        log.info("User {} logged out from session {}", userId, sessionId);
    }

    @Transactional
    public void logoutAll(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var sessions = deviceSessionRepository.findByUserAndRevokedFalse(user);
        sessions.forEach(session -> session.setRevoked(true));
        deviceSessionRepository.saveAll(sessions);
        log.info("User {} logged out from all devices", userId);
    }

    /**
     * Create a device session and return auth tokens
     *
     * @param bodyLocation optional location from the request body; missing
     *                     fields are taken from the HTTP request headers
     */
    @Transactional
    private AuthResponse createDeviceSessionAndReturnAuth(User user, DeviceLocationRequest bodyLocation) {
        // Get request context for device info
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        if (attributes != null) {
            request = attributes.getRequest();
        }

        String ipAddress = request != null ? getClientIpAddress(request) : "0.0.0.0";
        String userAgent = request != null && request.getHeader("User-Agent") != null ?
                request.getHeader("User-Agent") : "Unknown";

        // Location may come from the body, the headers, or both.
        DeviceLocationRequest location = locationResolver.resolve(bodyLocation, request);

        // Create device session
        DeviceSession session = DeviceSession.builder()
                .user(user)
                .deviceName(locationResolver.resolveDeviceName(request, getDeviceName(userAgent)))
                .deviceType(resolveDeviceType(request, userAgent))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastActiveAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))  // Refresh token expiration
                .refreshTokenHash(hashToken(TokenGenerator.generateSecureToken()))
                .build();
        session = deviceSessionRepository.save(session);

        // Persist the location reported by the device (if any)
        if (location != null) {
            applyLocation(session, location);
            session = deviceSessionRepository.save(session);
        }

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), session.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), session.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn((long) jwtService.getExpirationTimeInSeconds(accessToken))
                .build();
    }

    /**
     * Determine the device type from the request headers, falling back to a
     * guess based on the User-Agent.
     */
    private DeviceType resolveDeviceType(HttpServletRequest request, String userAgent) {
        DeviceType fromHeader = locationResolver.resolveDeviceType(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        if (userAgent != null) {
            String ua = userAgent.toLowerCase();
            if (ua.contains("android")) return DeviceType.ANDROID;
            if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return DeviceType.IOS;
            if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("x11")) return DeviceType.DESKTOP;
        }
        return DeviceType.WEB;
    }

    /**
     * Copy the reported coordinates onto the device session.
     */
    private void applyLocation(DeviceSession session, DeviceLocationRequest location) {
        if (location.mapX() != null) {
            session.setMapX(location.mapX());
        }
        if (location.mapY() != null) {
            session.setMapY(location.mapY());
        }
        if (location.city() != null && !location.city().isBlank()) {
            session.setCity(location.city());
        }
        if (location.country() != null && !location.country().isBlank()) {
            session.setCountry(location.country());
        }
        session.setLocationUpdatedAt(LocalDateTime.now());
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "CF-Connecting-IP",
                "X-Forwarded-Host",
                "X-Real-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0];
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Extract device name from User-Agent
     */
    private String getDeviceName(String userAgent) {
        if (userAgent == null) return "Unknown Device";

        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("Mobile")) return "Mobile";

        return "Web Browser";
    }

    /**
     * Hash token using SHA-256
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}