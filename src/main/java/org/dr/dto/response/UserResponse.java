package org.dr.dto.response;

import lombok.Builder;

@Builder
public record UserResponse(
        String id,
        String email,
        String username
) {
}
