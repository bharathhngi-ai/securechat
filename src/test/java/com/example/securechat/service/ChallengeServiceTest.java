package com.example.securechat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChallengeServiceTest {
    @Test
    void challengeIsUniqueAndCanOnlyBeConsumedOnce() {
        ChallengeService service = new ChallengeService();
        UUID userId = UUID.randomUUID();
        String first = service.issue(userId);
        String second = service.issue(userId);

        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(service.consume(userId, first)).isTrue();
        assertThat(service.consume(userId, first)).isFalse();
        assertThat(service.consume(UUID.randomUUID(), second)).isFalse();
    }
}
