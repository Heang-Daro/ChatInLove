package org.dr.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadInitResponse {
    private String uploadId;
    private long chunkSize;
    private long uploadOffset;
}
