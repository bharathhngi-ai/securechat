package com.example.securechat.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class ChallengeService {
    private static final long CHALLENGE_LIFETIME_SECONDS = 60;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public String issue(UUID userId) {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        challenges.put(key(userId, nonce), new Challenge(now.plusSeconds(CHALLENGE_LIFETIME_SECONDS)));
        return nonce;
    }

    public boolean consume(UUID userId, String nonce) {
        Challenge challenge = challenges.remove(key(userId, nonce));
        return challenge != null && challenge.expiresAt().isAfter(Instant.now());
    }

    private String key(UUID userId, String nonce) {
        return userId + ":" + nonce;
    }

    private record Challenge(Instant expiresAt) {
    }
}
