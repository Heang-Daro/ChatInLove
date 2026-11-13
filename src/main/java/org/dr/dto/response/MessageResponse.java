package org.dr.dto.response;

import lombok.Builder;
import org.dr.model.enums.MessageType;

import java.time.LocalDateTime;

@Builder
public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderUsername,
        MessageType type,
        String content,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        LocalDateTime deletedAt,
        String relatedCallId
) {
}