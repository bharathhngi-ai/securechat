package com.example.securechat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.securechat.entity.User;

class ClientSignatureVerifierTest {
    private final ClientSignatureVerifier verifier = new ClientSignatureVerifier(new PublicKeyService());

    @Test
    void verifiesConnectionProofAndRejectsChangedNonce() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(keyPair.getPublic().getEncoded());
        User user = new User(UUID.randomUUID(), publicKey, publicKey);
        String nonce = "one-time-random-challenge";
        String signature = sign(keyPair, "securechat-connect-v1\n" + user.getId() + "\n" + nonce);

        assertThat(verifier.verifyConnection(user, nonce, signature)).isTrue();
        assertThat(verifier.verifyConnection(user, nonce + "-changed", signature)).isFalse();
    }

    private String sign(KeyPair keyPair, String content) throws Exception {
        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        signer.initSign(keyPair.getPrivate());
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
}
