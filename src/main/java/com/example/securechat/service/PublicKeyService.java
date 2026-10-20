package com.example.securechat.service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class PublicKeyService {
    public PublicKey parseAndValidateRsaKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getUrlDecoder().decode(encodedKey);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            if (!(publicKey instanceof RSAPublicKey rsaKey) || rsaKey.getModulus().bitLength() < 3072) {
                throw new IllegalArgumentException("RSA keys must be at least 3072 bits.");
            }
            return publicKey;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid base64url RSA public key.", exception);
        }
    }
}
