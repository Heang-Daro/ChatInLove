package org.dr.controller;

import lombok.RequiredArgsConstructor;
import org.dr.service.StorageUrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Resolves Supabase Storage paths that were stored without a host.
 *
 * <p>Older messages stored the content as a project relative path such as
 * {@code /storage/v1/object/public/data_storage/<file>}. Clients that prefix
 * such a path with their own base URL would otherwise request the file from
 * this application instead of Supabase. This controller answers those
 * requests with a redirect to the real Supabase public URL.
 */
@RestController
@RequiredArgsConstructor
public class StorageProxyController {

    private final StorageUrlService storageUrlService;

    @GetMapping("/storage/v1/object/public/{bucket}/{fileName:.+}")
    public ResponseEntity<Void> redirectToStorage(
            @PathVariable String bucket,
            @PathVariable String fileName) {

        String target = storageUrlService.buildPublicUrl(bucket, fileName);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
