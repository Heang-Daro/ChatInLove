package org.dr.dto.response;

import lombok.Builder;

@Builder
public record UserResponse(
        String id,
        String email,
        String username,
        String profileImageUrl,
        String foregroundImageUrl
) {
    public UserResponse(String id, String email, String username) {
        this(id, email, username, null, null);
    }
}
