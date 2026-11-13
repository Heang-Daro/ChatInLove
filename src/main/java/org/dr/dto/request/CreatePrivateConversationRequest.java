package org.dr.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePrivateConversationRequest(
        @NotBlank(message = "Recipient ID is required")
        String recipientId
) {
}
