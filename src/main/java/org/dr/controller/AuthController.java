package org.dr.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dr.dto.request.ChangePasswordRequest;
import org.dr.dto.request.DeviceLocationRequest;
import org.dr.dto.request.LoginRequest;
import org.dr.dto.request.RefreshTokenRequest;
import org.dr.dto.request.RegisterRequest;
import org.dr.dto.request.UpdateProfileRequest;
import org.dr.dto.response.AuthResponse;
import org.dr.dto.response.DeviceSessionResponse;
import org.dr.dto.response.UserResponse;
import org.dr.model.User;
import org.dr.service.AuthenticationService;
import org.dr.service.DeviceSessionService;
import org.dr.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authService;
    private final DeviceSessionService deviceSessionService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(user.getId(), request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user, HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            return ResponseEntity.noContent().build();
        }
        authService.logout(user.getId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal User user) {
        authService.logoutAll(user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/principal")
    public ResponseEntity<UserResponse> getPrincipal(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail(), user.getUsername()));
    }

    @GetMapping("/devices")
    public ResponseEntity<List<DeviceSessionResponse>> getDevices(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(deviceSessionService.getUserDevices(user.getId()));
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> revokeDevice(@AuthenticationPrincipal User user, @PathVariable String id) {
        deviceSessionService.revokeDeviceSession(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/devices/location")
    public ResponseEntity<Void> updateDeviceLocation(@AuthenticationPrincipal User user,
            HttpServletRequest request,
            @RequestBody(required = false) DeviceLocationRequest location) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            return ResponseEntity.noContent().build();
        }
        deviceSessionService.updateLocation(user.getId(), sessionId, location, request);
        return ResponseEntity.noContent().build();
    }

    private String extractSessionId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty() || !jwtService.isTokenValid(token)) {
            return null;
        }
        try {
            return jwtService.extractSessionId(token);
        } catch (Exception e) {
            return null;
        }
    }
}
