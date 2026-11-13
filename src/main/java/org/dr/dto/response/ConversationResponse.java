package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ConversationResponse(
        String id,
        ConversationType type,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        List<ConversationMemberResponse> members,
        MessageResponse lastMessage
) {
}
