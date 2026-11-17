package org.dr.repository;

import org.dr.model.MediaUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MediaUploadSessionRepository extends JpaRepository<MediaUploadSession, String> {
    List<MediaUploadSession> findByStatusAndCreatedAtBefore(
            MediaUploadSession.UploadStatus status,
            LocalDateTime threshold
    );

    List<MediaUploadSession> findByUploaderId(String uploaderId);
}
