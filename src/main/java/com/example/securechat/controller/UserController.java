package com.example.securechat.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.securechat.dto.RegisterUserRequest;
import com.example.securechat.dto.UserPublicKeyResponse;
import com.example.securechat.entity.User;
import com.example.securechat.repository.UserRepository;
import com.example.securechat.service.UserDirectoryService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserDirectoryService userDirectoryService;
    private final UserRepository userRepository;

    public UserController(UserDirectoryService userDirectoryService, UserRepository userRepository) {
        this.userDirectoryService = userDirectoryService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public UserPublicKeyResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return toResponse(userDirectoryService.register(request));
    }

    @GetMapping("/{userId}/keys")
    public UserPublicKeyResponse publicKeys(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown blind user ID."));
        return toResponse(user);
    }

    private UserPublicKeyResponse toResponse(User user) {
        return new UserPublicKeyResponse(user.getId(),
                user.getEncryptionPublicKey(), user.getSigningPublicKey());
    }
}
