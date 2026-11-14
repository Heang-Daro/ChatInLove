package org.dr.service;

import lombok.RequiredArgsConstructor;
import org.dr.dto.response.UserResponse;
import org.dr.exception.ResourceNotFoundException;
import org.dr.model.User;
import org.dr.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProfileMediaService {

    private final UserRepository userRepository;
    private final FileService fileService;

    @Transactional
    public UserResponse uploadProfileImage(String userId, MultipartFile file) {
        return upload(userId, file, true);
    }

    @Transactional
    public UserResponse uploadForegroundImage(String userId, MultipartFile file) {
        return upload(userId, file, false);
    }

    private UserResponse upload(String userId, MultipartFile file, boolean profileImage) {
        validateImage(file);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        try {
            String publicUrl = fileService.uploadImage(file).url();
            if (profileImage) {
                user.setProfileImageUrl(publicUrl);
            } else {
                user.setForegroundImageUrl(publicUrl);
            }
            User saved = userRepository.save(user);
            return new UserResponse(saved.getId(), saved.getEmail(), saved.getUsername(),
                    saved.getProfileImageUrl(), saved.getForegroundImageUrl());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not upload image", e);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image to upload");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }
    }
}
