package com.example.securechat.dto;

import java.time.Instant;
import java.util.UUID;

public record RelayedMessage(
        UUID senderId,
        UUID receiverId,
        String iv,
        String ciphertext,
        Instant timestamp) {
}
