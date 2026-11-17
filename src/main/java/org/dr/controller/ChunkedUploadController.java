package org.dr.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.InitUploadRequest;
import org.dr.dto.response.UploadInitResponse;
import org.dr.dto.response.UploadResultResponse;
import org.dr.model.User;
import org.dr.service.ChunkedUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/media/uploads")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class ChunkedUploadController {

    private final ChunkedUploadService uploadService;

    /**
     * 1. Initialize resumable chunked upload session.
     */
    @PostMapping
    public ResponseEntity<UploadInitResponse> initUpload(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody InitUploadRequest request) throws IOException {

        String userId = user != null ? user.getId() : "anonymous";
        UploadInitResponse response = uploadService.initSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Query exact current Upload-Offset (bytes received so far) to resume.
     */
    @RequestMapping(method = RequestMethod.HEAD, value = "/{uploadId}")
    public ResponseEntity<Void> checkOffset(@PathVariable String uploadId) {
        long currentOffset = uploadService.getCurrentOffset(uploadId);
        if (currentOffset < 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .header("Upload-Offset", String.valueOf(currentOffset))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    /**
     * 3. Send binary chunk at specific Upload-Offset.
     */
    @PatchMapping("/{uploadId}")
    public ResponseEntity<UploadResultResponse> uploadChunk(
            @PathVariable String uploadId,
            @RequestHeader(value = "Upload-Offset", defaultValue = "0") long clientOffset,
            HttpServletRequest request) throws IOException {

        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        UploadResultResponse result = uploadService.appendChunk(
                uploadId,
                clientOffset,
                contentLength,
                request.getInputStream()
        );

        if (result.isCompleted()) {
            return ResponseEntity.ok()
                    .header("Upload-Offset", String.valueOf(result.getUploadedBytes()))
                    .body(result);
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header("Upload-Offset", String.valueOf(result.getUploadedBytes()))
                .build();
    }
}
