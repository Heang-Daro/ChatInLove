package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.ConversationMemberRole;

import java.time.LocalDateTime;

@Builder
public record ConversationMemberResponse(
        String userId,
        String username,
        ConversationMemberRole role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
) {
}
