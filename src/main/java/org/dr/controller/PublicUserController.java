package org.dr.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.dr.dto.response.UserResponse;
import org.dr.model.User;
import org.dr.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PublicUserController {
    private final UserRepository repository;

    @GetMapping({"/search", "/search/{username}"})
    public ResponseEntity<List<UserResponse>> findUserByUsername(
            @PathVariable(required = false) String username,
            @RequestParam(name = "username", required = false) String usernameQuery) {
        String searchTerm = username != null ? username : usernameQuery;
        if (searchTerm == null || searchTerm.isBlank()) return ResponseEntity.badRequest().build();

        List<User> users = repository.findByUsernameContainingIgnoreCase(searchTerm.trim());
        return ResponseEntity.ok(users.stream()
                .map(user -> new UserResponse(user.getId(), user.getEmail(), user.getUsername(),
                        user.getProfileImageUrl(), user.getForegroundImageUrl()))
                .collect(Collectors.toList()));
    }
}
