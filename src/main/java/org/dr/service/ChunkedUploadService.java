package org.dr.service;

import org.dr.dto.request.InitUploadRequest;
import org.dr.dto.response.UploadInitResponse;
import org.dr.dto.response.UploadResultResponse;
import org.dr.model.MediaUploadSession;
import org.dr.repository.MediaUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private final MediaUploadSessionRepository sessionRepo;
    private final VideoTranscodeService transcodeService;

    @Value("${app.upload.dir:./uploads/}")
    private String completedDirPath;

    @Value("${app.upload.temp-dir:./uploads/temp/}")
    private String tempDirPath;

    @Value("${app.upload.chunk-size:2097152}")
    private long defaultChunkSize;

    // Concurrent offset tracker for atomic multi-chunk updates
    private final Map<String, Long> activeOffsets = new ConcurrentHashMap<>();

    @Transactional
    public UploadInitResponse initSession(String userId, InitUploadRequest req) throws IOException {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path tempDir = Paths.get(tempDirPath);
        Files.createDirectories(tempDir);

        Path tempFile = tempDir.resolve(uploadId + ".part");
        if (!Files.exists(tempFile)) {
            Files.createFile(tempFile);
        }

        MediaUploadSession session = MediaUploadSession.builder()
                .uploadId(uploadId)
                .uploaderId(userId)
                .fileName(req.getFileName())
                .mimeType(req.getMimeType())
                .totalSize(req.getTotalSize())
                .uploadedBytes(0L)
                .expectedSha256(req.getFileHash())
                .status(MediaUploadSession.UploadStatus.UPLOADING)
                .tempFilePath(tempFile.toAbsolutePath().toString())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        sessionRepo.save(session);
        activeOffsets.put(uploadId, 0L);

        return UploadInitResponse.builder()
                .uploadId(uploadId)
                .chunkSize(defaultChunkSize)
                .uploadOffset(0L)
                .build();
    }

    public long getCurrentOffset(String uploadId) {
        Long cached = activeOffsets.get(uploadId);
        if (cached != null) return cached;

        return sessionRepo.findById(uploadId)
                .map(MediaUploadSession::getUploadedBytes)
                .orElse(-1L);
    }

    public synchronized UploadResultResponse appendChunk(
            String uploadId,
            long clientOffset,
            long chunkLength,
            InputStream chunkStream) throws IOException {

        MediaUploadSession session = sessionRepo.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));

        if (session.getStatus() == MediaUploadSession.UploadStatus.COMPLETED) {
            return UploadResultResponse.builder()
                    .uploadId(uploadId)
                    .uploadedBytes(session.getTotalSize())
                    .totalBytes(session.getTotalSize())
                    .completed(true)
                    .fileUrl("/api/media/stream/" + uploadId)
                    .build();
        }

        long currentServerOffset = getCurrentOffset(uploadId);

        // Guard against out-of-order or duplicate chunk transmission
        if (clientOffset != currentServerOffset) {
            log.warn("Offset mismatch for {}: Client sent {}, Server has {}", uploadId, clientOffset, currentServerOffset);
            throw new IllegalStateException("Offset mismatch. Expected: " + currentServerOffset + ", but client sent: " + clientOffset);
        }

        Path targetPath = Paths.get(session.getTempFilePath());

        // Stream chunk bytes directly to file using NIO FileChannel without loading into memory
        try (RandomAccessFile raf = new RandomAccessFile(targetPath.toFile(), "rw");
             FileChannel fileChannel = raf.getChannel();
             ReadableByteChannel inputChannel = Channels.newChannel(chunkStream)) {

            fileChannel.position(clientOffset);
            ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024); // 64KB direct buffer

            long bytesWrittenForChunk = 0;
            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                bytesWrittenForChunk += fileChannel.write(buffer);
                buffer.clear();
            }

            long newOffset = clientOffset + bytesWrittenForChunk;
            activeOffsets.put(uploadId, newOffset);
            session.setUploadedBytes(newOffset);
            session.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            sessionRepo.save(session);

            // Assembly & Final Integrity Verification when complete
            if (newOffset >= session.getTotalSize()) {
                return finalizeUpload(session, targetPath);
            }

            return UploadResultResponse.builder()
                    .uploadId(uploadId)
                    .uploadedBytes(newOffset)
                    .totalBytes(session.getTotalSize())
                    .completed(false)
                    .build();
        }
    }

    private UploadResultResponse finalizeUpload(MediaUploadSession session, Path tempPath) throws IOException {
        log.info("Finalizing media upload session: {}", session.getUploadId());

        // Streaming SHA-256 Checksum verification
        if (session.getExpectedSha256() != null && !session.getExpectedSha256().isBlank()) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream is = Files.newInputStream(tempPath)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        digest.update(buf, 0, r);
                    }
                }
                String computedHash = HexFormat.of().formatHex(digest.digest());
                if (!computedHash.equalsIgnoreCase(session.getExpectedSha256().trim())) {
                    log.error("Integrity check failed for {}: expected {}, computed {}",
                            session.getUploadId(), session.getExpectedSha256(), computedHash);
                    session.setStatus(MediaUploadSession.UploadStatus.FAILED);
                    sessionRepo.save(session);
                    throw new SecurityException("SHA-256 checksum mismatch! Upload corrupted in transit.");
                }
            } catch (Exception e) {
                if (e instanceof SecurityException) throw (SecurityException) e;
                log.warn("SHA-256 calculation issue: {}", e.getMessage());
            }
        }

        Path completedDir = Paths.get(completedDirPath);
        Files.createDirectories(completedDir);
        String finalName = session.getUploadId() + "_" + sanitizeFileName(session.getFileName());
        Path finalPath = completedDir.resolve(finalName);
        Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

        session.setFinalFilePath(finalPath.toAbsolutePath().toString());
        session.setStatus(MediaUploadSession.UploadStatus.COMPLETED);
        session.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        sessionRepo.save(session);
        activeOffsets.remove(session.getUploadId());

        String streamUrl = "/api/media/stream/" + session.getUploadId();
        String hlsUrl = null;

        // Trigger Async HLS transcoding if media is video
        if (session.getMimeType() != null && session.getMimeType().toLowerCase().startsWith("video/")) {
            transcodeService.transcodeToHlsAsync(session.getUploadId(), finalPath);
            hlsUrl = "/api/media/hls/" + session.getUploadId() + "/master.m3u8";
        }

        return UploadResultResponse.builder()
                .uploadId(session.getUploadId())
                .uploadedBytes(session.getTotalSize())
                .totalBytes(session.getTotalSize())
                .completed(true)
                .fileUrl(streamUrl)
                .hlsUrl(hlsUrl)
                .build();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "file.bin";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
