package org.dr.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String userId,
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String tokenType
) {
}