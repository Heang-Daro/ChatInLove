package org.dr.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitUploadRequest {
    @NotBlank(message = "File name must not be blank")
    private String fileName;

    @NotBlank(message = "MIME type must not be blank")
    private String mimeType;

    @NotNull(message = "Total size is required")
    @Positive(message = "Total size must be positive")
    private Long totalSize;

    private String fileHash;
}
