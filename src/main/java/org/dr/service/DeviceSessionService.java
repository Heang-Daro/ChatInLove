package org.dr.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.DeviceLocationRequest;
import org.dr.dto.response.DeviceSessionResponse;
import org.dr.exception.ResourceNotFoundException;
import org.dr.model.DeviceSession;
import org.dr.model.User;
import org.dr.repository.DeviceSessionRepository;
import org.dr.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceSessionService {
    private final DeviceSessionRepository deviceSessionRepository;
    private final UserRepository userRepository;
    private final DeviceLocationResolver locationResolver;

    /**
     * Get the caller's current device session by session ID extracted from their JWT.
     */
    @Transactional(readOnly = true)
    public DeviceSessionResponse getCurrentDevice(String userId, String sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DeviceSession session = deviceSessionRepository.findByUserAndIdAndRevokedFalse(user, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Current device session not found"));

        return toResponse(session);
    }

    /**
     * Get all active device sessions for a user
     */
    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> getUserDevices(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return deviceSessionRepository.findByUserAndRevokedFalseAndExpiresAtAfter(user, LocalDateTime.now(ZoneOffset.UTC))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Revoke a specific device session
     */
    @Transactional
    public void revokeDeviceSession(String userId, String sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var session = deviceSessionRepository.findByUserAndIdAndRevokedFalse(user, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Device session not found"));

        session.setRevoked(true);
        deviceSessionRepository.save(session);
        log.info("User {} revoked session {}", userId, sessionId);
    }

    /**
     * Record the location reported by a device.
     *
     * <p>The resolver merges the request body with an IP-based lookup, so any
     * field the client left out (latitude, longitude, city, country) is filled
     * from the network when possible. Fields still unknown keep the value
     * already cached on the session.
     *
     * @param userId    owner of the device session
     * @param sessionId device session to update
     * @param body      location from the request body, may be {@code null}
     * @param request   the current HTTP request, used for the IP lookup
     */
    @Transactional
    public void updateLocation(String userId, String sessionId,
            DeviceLocationRequest body, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var session = deviceSessionRepository.findByUserAndIdAndRevokedFalse(user, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Device session not found"));

        DeviceLocationRequest location = locationResolver.resolve(body, request);
        if (location == null) {
            // Nothing reported and nothing derivable - keep the cached location.
            return;
        }

        // Only overwrite when we actually have a value; otherwise keep what is cached.
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

        // Fill IP from the network if the session doesn't have one yet
        if (request != null && (session.getIpAddress() == null || session.getIpAddress().isBlank())) {
            session.setIpAddress(DeviceLocationResolver.extractClientIp(request));
        }

        session.setLocationUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceSessionRepository.save(session);

        log.info("User {} updated location for session {}", userId, sessionId);
    }

    /**
     * Clean up expired sessions
     */
    @Transactional
    public void cleanupExpiredSessions() {
        deviceSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
        log.debug("Cleaned up expired device sessions");
    }

    private DeviceSessionResponse toResponse(DeviceSession session) {
        return DeviceSessionResponse.builder()
                .id(session.getId())
                .deviceName(session.getDeviceName())
                .deviceType(session.getDeviceType())
                .ipAddress(session.getIpAddress())
                .mapX(session.getMapX())
                .mapY(session.getMapY())
                .city(session.getCity())
                .country(session.getCountry())
                .locationUpdatedAt(session.getLocationUpdatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .expiresAt(session.getExpiresAt())
                .revoked(session.isRevoked())
                .build();
    }
}