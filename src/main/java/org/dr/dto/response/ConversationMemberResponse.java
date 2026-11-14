package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.ConversationMemberRole;
import org.dr.model.enums.ConversationStatus;

import java.time.LocalDateTime;

@Builder
public record ConversationMemberResponse(
        String userId,
        String username,
        ConversationMemberRole role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        String profileImageUrl
) {
    public ConversationMemberResponse(String userId, String username, ConversationMemberRole role,
            LocalDateTime joinedAt, LocalDateTime leftAt) {
        this(userId, username, role, joinedAt, leftAt, null);
    }
}
