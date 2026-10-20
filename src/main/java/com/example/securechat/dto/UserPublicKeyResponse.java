package com.example.securechat.dto;

import java.util.UUID;

public record UserPublicKeyResponse(
        UUID userId,
        String encryptionPublicKey,
        String signingPublicKey) {
}
