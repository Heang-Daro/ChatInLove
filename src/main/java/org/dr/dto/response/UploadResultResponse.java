package org.dr.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResultResponse {
    private String uploadId;
    private long uploadedBytes;
    private long totalBytes;
    private boolean completed;
    private String fileUrl;
    private String hlsUrl;
}
