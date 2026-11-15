package org.dr.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.dr.dto.response.UserPresenceResponse;
import org.dr.dto.response.UserResponse;
import org.dr.model.User;
import org.dr.repository.UserRepository;
import org.dr.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PublicUserController {

    private final UserRepository repository;
    private final PresenceService presenceService;

    @GetMapping({"/search", "/search/{username}"})
    public ResponseEntity<List<UserResponse>> findUserByUsername(
            @PathVariable(required = false) String username,
            @RequestParam(name = "username", required = false) String usernameQuery) {
        String searchTerm = username != null ? username : usernameQuery;
        if (searchTerm == null || searchTerm.isBlank()) return ResponseEntity.badRequest().build();
        List<User> users = repository.findByUsernameContainingIgnoreCase(searchTerm.trim());
        return ResponseEntity.ok(users.stream()
                .map(u -> new UserResponse(u.getId(), u.getEmail(), u.getUsername(),
                        u.getProfileImageUrl(), u.getForegroundImageUrl()))
                .collect(Collectors.toList()));
    }

    /** GET /api/users/online — all currently-online users */
    @GetMapping("/online")
    public ResponseEntity<List<UserPresenceResponse>> getOnlineUsers() {
        Set<String> onlineIds = presenceService.onlineUserIds();
        if (onlineIds.isEmpty()) return ResponseEntity.ok(List.of());
        List<User> users = repository.findAllById(onlineIds);
        return ResponseEntity.ok(users.stream()
                .map(u -> new UserPresenceResponse(
                        u.getId(), u.getUsername(), u.getProfileImageUrl(), true, 0L))
                .collect(Collectors.toList()));
    }

    /** GET /api/users/{userId}/presence — single-user presence */
    @GetMapping("/{userId}/presence")
    public ResponseEntity<UserPresenceResponse> getUserPresence(@PathVariable String userId) {
        return repository.findById(userId).map(u -> {
            boolean online  = presenceService.isOnline(userId);
            Long ls         = presenceService.getLastSeen(userId);
            long lastSeen   = (online || ls == null) ? 0L : ls;
            return ResponseEntity.ok(new UserPresenceResponse(
                    u.getId(), u.getUsername(), u.getProfileImageUrl(), online, lastSeen));
        }).orElse(ResponseEntity.notFound().build());
    }
}
