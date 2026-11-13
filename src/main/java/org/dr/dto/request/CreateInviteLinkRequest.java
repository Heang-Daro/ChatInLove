package org.dr.dto.request;

import jakarta.annotation.Nullable;

public record CreateInviteLinkRequest(
        @Nullable
        Long expirationMinutes,

        @Nullable
        Integer maxUses
) {
}
