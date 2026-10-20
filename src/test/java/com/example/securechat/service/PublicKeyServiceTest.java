package com.example.securechat.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PublicKeyServiceTest {
    private final PublicKeyService service = new PublicKeyService();

    @Test
    void acceptsStrongRsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        String key = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        service.parseAndValidateRsaKey(key);
    }

    @Test
    void rejectsWeakRsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidateRsaKey(key));
    }

    @Test
    void rejectsInvalidKeyEncoding() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidateRsaKey("not-a-key"));
    }
}
