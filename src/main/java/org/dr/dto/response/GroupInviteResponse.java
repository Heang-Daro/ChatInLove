package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.InviteStatus;

import java.time.LocalDateTime;

@Builder
public record GroupInviteResponse(
        String id,
        String conversationId,
        String conversationName,
        String invitedByUsername,
        InviteStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
}
