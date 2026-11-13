package org.dr.controller;

import lombok.RequiredArgsConstructor;
import org.dr.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/api/files/upload")
    public ResponseEntity<FileService.UploadResult> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            FileService.UploadResult result = fileService.upload(file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}