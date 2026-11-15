package org.dr.dto.response;

public record UserPresenceResponse(
        String userId,
        String username,
        String profileImageUrl,
        boolean online,
        long lastSeen   // epoch millis; 0 when online
) {}
