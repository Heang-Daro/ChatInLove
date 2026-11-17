package org.dr.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.model.MediaUploadSession;
import org.dr.repository.MediaUploadSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class MediaStreamingController {

    private final MediaUploadSessionRepository sessionRepo;

    @Value("${app.upload.hls-dir:./uploads/hls/}")
    private String hlsOutputDir;

    /**
     * HTTP 206 Partial Content Byte-Range streaming for instant video playback & seek scrubbing.
     */
    @GetMapping("/stream/{uploadId}")
    public ResponseEntity<ResourceRegion> streamMedia(
            @PathVariable String uploadId,
            @RequestHeader HttpHeaders headers) throws IOException {

        MediaUploadSession session = sessionRepo.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Media session not found: " + uploadId));

        if (session.getFinalFilePath() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        File file = new File(session.getFinalFilePath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = new FileSystemResource(file);
        long contentLength = file.length();
        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(session.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(1024 * 1024L, end - start + 1); // 1MB buffer slice

            ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(region);
        } else {
            // Default streaming slice for initial fast buffer (<1s startup)
            long rangeLength = Math.min(1024 * 1024L, contentLength);
            ResourceRegion region = new ResourceRegion(resource, 0, rangeLength);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(region);
        }
    }

    /**
     * HLS Adaptive Bitrate streaming (.m3u8 playlist & .ts segments).
     */
    @GetMapping("/hls/{uploadId}/{segmentName}")
    public ResponseEntity<Resource> getHlsSegment(
            @PathVariable String uploadId,
            @PathVariable String segmentName) {

        Path segmentPath = Paths.get(hlsOutputDir, uploadId, segmentName);
        File file = segmentPath.toFile();

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String contentType = segmentName.endsWith(".m3u8")
                ? "application/vnd.apple.mpegurl"
                : "video/MP2T";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }
}
