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

    public UploadResult upload(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "file_" + System.currentTimeMillis();
        }

        String extension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalFilename.substring(dotIdx).toLowerCase();
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        MessageType messageType = determineMessageType(file.getContentType(), originalFilename);

        // Supabase Storage REST API Endpoint for file upload / upsert
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, uniqueFileName);

        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            ResponseEntity<String> response = restClient.post()
                    .uri(uploadUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("x-upsert", "true") // អនុញ្ញាតឱ្យសរសេរជាន់ ឬបង្កើតថ្មី
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(file.getBytes())
                    .retrieve()
                    .toEntity(String.class);

            log.info("Uploaded file successfully to Supabase Storage: {}. Status: {}", uniqueFileName, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase: {}", e.getMessage());
            throw new IOException("Failed to upload file to Supabase storage: " + e.getMessage(), e);
        }

        // Construct Full Public URL for Supabase File (Full URL ពេញលេញសម្រាប់ Save ចូល Database)
        // Always absolute so the database holds a fully qualified, resolvable URL.
        String fileUrl = storageUrlService.buildPublicUrl(uniqueFileName);

        return new UploadResult(fileUrl, originalFilename, messageType);
    }

    public MessageType determineMessageType(String contentType, String fileName) {
        if (contentType != null) {
            String mime = contentType.toLowerCase();
            if (mime.startsWith("image/")) return MessageType.IMAGE;
            if (mime.startsWith("audio/")) return MessageType.VOICE;
            if (mime.startsWith("video/")) return MessageType.VIDEO;
        }

        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")) {
                return MessageType.IMAGE;
            }
            if (lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.contains("voice_")) {
                return MessageType.VOICE;
            }
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv")) {
                return MessageType.VIDEO;
            }
        }

        return MessageType.FILE;
    }

    public record UploadResult(String url, String originalName, MessageType messageType) {}
}