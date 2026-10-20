package com.example.securechat.service;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.example.securechat.entity.User;

@Service
public class ClientSignatureVerifier {
    private final PublicKeyService publicKeyService;

    public ClientSignatureVerifier(PublicKeyService publicKeyService) {
        this.publicKeyService = publicKeyService;
    }

    public boolean verifyConnection(User user, String nonce, String encodedSignature) {
        try {
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(new PSSParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            verifier.initVerify(publicKeyService.parseAndValidateRsaKey(user.getSigningPublicKey()));
            verifier.update(("securechat-connect-v1\n" + user.getId() + "\n" + nonce)
                    .getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getUrlDecoder().decode(encodedSignature));
        } catch (Exception exception) {
            return false;
        }
    }
}
