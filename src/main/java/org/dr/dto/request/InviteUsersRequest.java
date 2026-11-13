package org.dr.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InviteUsersRequest(
        @NotEmpty(message = "At least one user ID is required")
        List<String> userIds
) {
}
