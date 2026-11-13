package org.dr.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateGroupConversationRequest(
    @NotBlank(message = "Group name is required")
    String name,
    
    String description,
    
    @NotEmpty(message = "Group must have at least one member")
    List<String> memberIds // បញ្ជី ID របស់អ្នកដែលត្រូវដាក់បញ្ចូលក្នុងគ្រុប
) {}