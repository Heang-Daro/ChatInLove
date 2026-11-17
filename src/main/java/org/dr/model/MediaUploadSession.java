package org.dr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "media_upload_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadSession {

    @Id
    @Column(length = 64)
    private String uploadId;

    @Column(nullable = false, length = 64)
    private String uploaderId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Long totalSize;

    @Column(nullable = false)
    @Builder.Default
    private Long uploadedBytes = 0L;

    @Column(length = 64)
    private String expectedSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UploadStatus status = UploadStatus.UPLOADING;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tempFilePath;

    @Column(columnDefinition = "TEXT")
    private String finalFilePath;

    @Column(columnDefinition = "TEXT")
    private String hlsMasterPlaylistPath;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (uploadedBytes == null) uploadedBytes = 0L;
        if (status == null) status = UploadStatus.UPLOADING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public enum UploadStatus {
        UPLOADING,
        COMPLETED,
        PROCESSING,
        FAILED,
        EXPIRED
    }
}
