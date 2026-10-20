package com.example.securechat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 2000) String encryptionPublicKey,
        @NotBlank @Size(max = 2000) String signingPublicKey) {
}
