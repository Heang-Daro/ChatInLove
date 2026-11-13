package org.dr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for building absolute Supabase Storage URLs.
 *
 * <p>Clients have historically stored file references in different shapes:
 * a full URL ({@code https://<project>.supabase.co/storage/...}), a project
 * relative path ({@code /storage/v1/object/public/<bucket>/<file>}), or a
 * local path ({@code /uploads/<file>}). This service normalises anything
 * that looks like a Supabase storage path back into its absolute form so the
 * database always holds a fully qualified URL.
 */
@Service
public class StorageUrlService {

    /** Path prefix used by the Supabase Storage REST API. */
    public static final String STORAGE_PATH_PREFIX = "/storage/v1/object/public/";

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    public String getBucketName() {
        return bucketName;
    }

    public String getSupabaseUrl() {
        return stripTrailingSlash(supabaseUrl);
    }

    /**
     * Expand a stored reference into an absolute URL.
     *
     * <p>Absolute URLs and non-Supabase paths (for example {@code /uploads/...})
     * are returned unchanged.
     */
    public String toAbsoluteUrl(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String value = content.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith(STORAGE_PATH_PREFIX)) {
            return getSupabaseUrl() + value;
        }
        return value;
    }

    /**
     * Build the public URL of a file inside the configured bucket.
     */
    public String buildPublicUrl(String fileName) {
        return getSupabaseUrl() + STORAGE_PATH_PREFIX + bucketName + "/" + fileName;
    }

    /**
     * Build the public URL of a file stored in an arbitrary bucket.
     */
    public String buildPublicUrl(String bucket, String fileName) {
        return getSupabaseUrl() + STORAGE_PATH_PREFIX + bucket + "/" + fileName;
    }

    private String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
