package org.dr.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "Group name is required")
        String name,

        String description,

        @NotEmpty(message = "At least 3 members are required")
        List<String> memberIds,

        @Min(value = 0, message = "Minimum invited members must be at least 0")
        Integer minInvitedMembers
) {
}
