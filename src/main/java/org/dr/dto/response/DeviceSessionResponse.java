package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.DeviceType;

import java.time.LocalDateTime;

@Builder
public record DeviceSessionResponse(
        String id,
        String deviceName,
        DeviceType deviceType,
        String ipAddress,
        Double mapX,
        Double mapY,
        String city,
        String country,
        LocalDateTime locationUpdatedAt,
        LocalDateTime lastActiveAt,
        LocalDateTime expiresAt,
        boolean revoked
) {
}
