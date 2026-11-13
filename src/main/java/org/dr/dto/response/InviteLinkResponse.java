package org.dr.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record InviteLinkResponse(
        String id,
        String conversationId,
        String token,
        String inviteUrl,
        LocalDateTime expiresAt,
        Integer maxUses,
        Integer usesCount,
        boolean revoked,
        LocalDateTime createdAt
) {
}
