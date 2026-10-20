package com.example.securechat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EncryptedMessageRequest(
        @NotNull UUID senderId,
        @NotNull UUID receiverId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{16}$") String iv,
        @NotBlank @Size(max = 4_000_000) String ciphertext) {
}
