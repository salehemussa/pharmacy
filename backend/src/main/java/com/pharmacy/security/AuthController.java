package com.pharmacy.security;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthService.AuthResponse refresh(@Valid @RequestBody AuthService.RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@RequestBody(required = false) AuthService.LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public AuthService.UserProfile me() {
        return authService.me();
    }

    @PutMapping("/profile")
    public AuthService.UserProfile updateProfile(@Valid @RequestBody AuthService.ProfileUpdateRequest request) {
        return authService.updateProfile(request);
    }

    @PostMapping("/change-password")
    public AuthService.AuthResponse changePassword(@Valid @RequestBody AuthService.ChangePasswordRequest request) {
        return authService.changePassword(request);
    }
}
