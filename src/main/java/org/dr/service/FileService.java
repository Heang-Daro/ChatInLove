package org.dr.service;

import lombok.extern.slf4j.Slf4j;
import org.dr.model.enums.MessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class FileService {
    private final StorageUrlService storageUrlService;

    public FileService(StorageUrlService storageUrlService) {
        this.storageUrlService = storageUrlService;
    }

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    private final RestClient restClient = RestClient.create();

    private String getCleanKey() {
        if (serviceRoleKey == null) return "";
        String key = serviceRoleKey.trim();
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.toLowerCase(Locale.ROOT).startsWith("bearer ")) key = key.substring(7).trim();
        return key;
    }

    public UploadResult upload(MultipartFile file) throws IOException {
        return uploadToStorage(file, determineMessageType(file.getContentType(), file.getOriginalFilename()));
    }

    /** Upload only a validated image; profile media are capped at 5 MB. */
    public UploadResult uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IOException("Image file is empty");
        if (file.getSize() > 5L * 1024 * 1024) throw new IOException("Image must be 5 MB or smaller");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IOException("Only image files are allowed");
        }
        return uploadToStorage(file, MessageType.IMAGE);
    }

    private UploadResult uploadToStorage(MultipartFile file, MessageType messageType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) originalFilename = "file_" + System.currentTimeMillis();

        String extension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) extension = originalFilename.substring(dotIdx).toLowerCase(Locale.ROOT);
        String uniqueFileName = UUID.randomUUID() + extension;
        String cleanKey = getCleanKey();
        if (cleanKey.isEmpty() || cleanKey.contains("your_supabase_service_role_key_here")) {
            log.error("SUPABASE_SERVICE_ROLE_KEY is not properly set");
            throw new IOException("Supabase storage credentials are missing or invalid");
        }

        String cleanBaseUrl = supabaseUrl != null && supabaseUrl.endsWith("/")
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", cleanBaseUrl, bucketName, uniqueFileName);
        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            ResponseEntity<String> response = restClient.post()
                    .uri(uploadUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cleanKey)
                    .header("apikey", cleanKey)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(file.getBytes())
                    .retrieve()
                    .toEntity(String.class);
            log.info("Uploaded file {} to Supabase Storage with status {}", uniqueFileName, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase: {}", e.getMessage());
            throw new IOException("Failed to upload file to Supabase storage", e);
        }
        return new UploadResult(storageUrlService.buildPublicUrl(uniqueFileName), originalFilename, messageType);
    }

    public MessageType determineMessageType(String contentType, String fileName) {
        if (contentType != null) {
            String mime = contentType.toLowerCase(Locale.ROOT);
            if (mime.startsWith("image/")) return MessageType.IMAGE;
            if (mime.startsWith("audio/")) return MessageType.VOICE;
            if (mime.startsWith("video/")) return MessageType.VIDEO;
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")) return MessageType.IMAGE;
            if (lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.contains("voice_")) return MessageType.VOICE;
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv")) return MessageType.VIDEO;
        }
        return MessageType.FILE;
    }

    public record UploadResult(String url, String originalName, MessageType messageType) {}
}
