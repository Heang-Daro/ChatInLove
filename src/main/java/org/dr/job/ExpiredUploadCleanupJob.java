package org.dr.job;

import org.dr.model.MediaUploadSession;
import org.dr.repository.MediaUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredUploadCleanupJob {

    private final MediaUploadSessionRepository sessionRepo;

    @Value("${app.upload.ttl-hours:48}")
    private int ttlHours;

    // Runs once every 24 hours at 03:00 UTC
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupAbandonedUploads() {
        log.info("Running scheduled cleanup for expired chunked upload sessions (TTL: {} hours)...", ttlHours);
        LocalDateTime threshold = LocalDateTime.now(ZoneOffset.UTC).minusHours(ttlHours);

        List<MediaUploadSession> expiredSessions = sessionRepo.findByStatusAndCreatedAtBefore(
                MediaUploadSession.UploadStatus.UPLOADING, threshold);

        for (MediaUploadSession session : expiredSessions) {
            try {
                if (session.getTempFilePath() != null) {
                    File tempFile = new File(session.getTempFilePath());
                    if (tempFile.exists()) {
                        boolean deleted = tempFile.delete();
                        log.info("Deleted orphaned chunked temp file {}: {}", tempFile.getName(), deleted);
                    }
                }
                session.setStatus(MediaUploadSession.UploadStatus.EXPIRED);
                session.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                sessionRepo.save(session);
            } catch (Exception e) {
                log.error("Failed to clean up expired session {}: {}", session.getUploadId(), e.getMessage());
            }
        }
    }
}
