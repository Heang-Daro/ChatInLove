package org.dr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.DeviceLocationRequest;
import org.dr.dto.request.LoginRequest;
import org.dr.dto.request.RefreshTokenRequest;
import org.dr.dto.request.RegisterRequest;
import org.dr.dto.request.UpdateProfileRequest;
import org.dr.dto.response.AuthResponse;
import org.dr.dto.response.UserResponse;
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
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
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
    private final FileService fileService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) throw new DuplicateResourceException("Email already registered: " + normalizedEmail);
        User user = User.builder().email(normalizedEmail).username(request.username())
                .passwordHash(passwordEncoder.encode(request.password())).build();
        user = userRepository.save(user);
        log.info("User registered: {}", user.getEmail());
        return createDeviceSessionAndReturnAuth(user, request.location());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw new InvalidCredentialsException("Invalid email or password");
        log.info("User logged in: {}", user.getEmail());
        return createDeviceSessionAndReturnAuth(user, request.location());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtService.isTokenValid(request.refreshToken())) throw new InvalidCredentialsException("Invalid or expired refresh token");
        String userId = jwtService.extractUserId(request.refreshToken());
        String sessionId = jwtService.extractSessionId(request.refreshToken());
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DeviceSession session = deviceSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) throw new InvalidCredentialsException("Session expired or revoked");
        session.setLastActiveAt(LocalDateTime.now());
        deviceSessionRepository.save(session);
        String accessToken = jwtService.generateAccessToken(user.getId(), sessionId);
        return AuthResponse.builder().userId(user.getId()).accessToken(accessToken).tokenType("Bearer")
                .expiresIn((long) jwtService.getExpirationTimeInSeconds(accessToken)).build();
    }

    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndIdNot(normalizedEmail, userId)) throw new DuplicateResourceException("Email already registered: " + normalizedEmail);
        user.setEmail(normalizedEmail);
        user.setUsername(request.username().trim());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponse(String userId) {
        return toUserResponse(userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getProfileImageUrl(), user.getForegroundImageUrl());
    }

    @Transactional
    public UserResponse uploadProfileImage(String userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        FileService.UploadResult result = fileService.upload(file);
        user.setProfileImageUrl(result.url());
        User saved = userRepository.save(user);
        log.info("Profile image updated for user {}", userId);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getUsername(),
                saved.getProfileImageUrl(), saved.getForegroundImageUrl());
    }

    @Transactional
    public UserResponse uploadForegroundImage(String userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        FileService.UploadResult result = fileService.upload(file);
        user.setForegroundImageUrl(result.url());
        User saved = userRepository.save(user);
        log.info("Foreground image updated for user {}", userId);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getUsername(),
                saved.getProfileImageUrl(), saved.getForegroundImageUrl());
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) throw new InvalidCredentialsException("Current password is incorrect");
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) throw new InvalidCredentialsException("New password must be different from the current password");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user {}", user.getEmail());
    }

    @Transactional
    public void logout(String userId, String sessionId) {
        DeviceSession session = deviceSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!session.getUser().getId().equals(userId)) throw new InvalidCredentialsException("Invalid session");
        session.setRevoked(true);
        deviceSessionRepository.save(session);
        log.info("User {} logged out from session {}", userId, sessionId);
    }

    @Transactional
    public void logoutAll(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var sessions = deviceSessionRepository.findByUserAndRevokedFalse(user);
        sessions.forEach(session -> session.setRevoked(true));
        deviceSessionRepository.saveAll(sessions);
        log.info("User {} logged out from all devices", userId);
    }

    @Transactional
    private AuthResponse createDeviceSessionAndReturnAuth(User user, DeviceLocationRequest bodyLocation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        String ipAddress = request != null ? getClientIpAddress(request) : "0.0.0.0";
        String userAgent = request != null && request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown";
        DeviceLocationRequest location = locationResolver.resolve(bodyLocation, request);
        DeviceSession session = DeviceSession.builder().user(user)
                .deviceName(locationResolver.resolveDeviceName(request, getDeviceName(userAgent)))
                .deviceType(resolveDeviceType(request, userAgent)).ipAddress(ipAddress).userAgent(userAgent)
                .lastActiveAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(7))
                .refreshTokenHash(hashToken(TokenGenerator.generateSecureToken())).build();
        session = deviceSessionRepository.save(session);
        if (location != null) {
            applyLocation(session, location);
            session = deviceSessionRepository.save(session);
        }
        String accessToken = jwtService.generateAccessToken(user.getId(), session.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), session.getId());
        return AuthResponse.builder().userId(user.getId()).accessToken(accessToken).refreshToken(refreshToken)
                .tokenType("Bearer").expiresIn((long) jwtService.getExpirationTimeInSeconds(accessToken)).build();
    }

    private DeviceType resolveDeviceType(HttpServletRequest request, String userAgent) {
        DeviceType fromHeader = locationResolver.resolveDeviceType(request);
        if (fromHeader != null) return fromHeader;
        if (userAgent != null) {
            String ua = userAgent.toLowerCase();
            if (ua.contains("android")) return DeviceType.ANDROID;
            if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return DeviceType.IOS;
            if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("x11")) return DeviceType.DESKTOP;
        }
        return DeviceType.WEB;
    }

    private void applyLocation(DeviceSession session, DeviceLocationRequest location) {
        if (location.mapX() != null) session.setMapX(location.mapX());
        if (location.mapY() != null) session.setMapY(location.mapY());
        if (location.city() != null && !location.city().isBlank()) session.setCity(location.city());
        if (location.country() != null && !location.country().isBlank()) session.setCountry(location.country());
        session.setLocationUpdatedAt(LocalDateTime.now());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "CF-Connecting-IP", "X-Forwarded-Host", "X-Real-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private String getDeviceName(String userAgent) {
        if (userAgent == null) return "Unknown Device";
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("Mobile")) return "Mobile";
        return "Web Browser";
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) hex.append('0');
                hex.append(value);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
