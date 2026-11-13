package org.dr.dto.request;

import org.dr.model.enums.MessageType;

public record SendMessageRequest(
        String content,
        MessageType messageType
) {
}
