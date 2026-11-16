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
        String relatedCallId,
        /**
         * Monotonic server-side cursor. Clients send it back as
         * {@code ?after=<sequenceId>} to fetch only the messages they missed
         * instead of the newest page.
         */
        Long sequenceId
) {
    /** Backwards compatible constructor without the sequence id. */
    public MessageResponse(String id, String conversationId, String senderId, String senderUsername,
                           MessageType type, String content, boolean isRead, LocalDateTime createdAt,
                           LocalDateTime editedAt, LocalDateTime deletedAt, String relatedCallId) {
        this(id, conversationId, senderId, senderUsername, type, content, isRead, createdAt,
                editedAt, deletedAt, relatedCallId, null);
    }
}
