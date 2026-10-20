package com.example.securechat.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blind_users")
public class User {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "encryption_public_key", nullable = false, length = 2000)
    private String encryptionPublicKey;

    @Column(name = "signing_public_key", nullable = false, length = 2000)
    private String signingPublicKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(UUID id, String encryptionPublicKey, String signingPublicKey) {
        this.id = id;
        this.encryptionPublicKey = encryptionPublicKey;
        this.signingPublicKey = signingPublicKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public String getSigningPublicKey() {
        return signingPublicKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
