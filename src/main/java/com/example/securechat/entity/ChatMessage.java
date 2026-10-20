package com.example.securechat.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "encrypted_messages", indexes = {
        @Index(name = "idx_message_receiver", columnList = "receiver_id,timestamp")
})
@IdClass(ChatMessage.MessageKey.class)
public class ChatMessage {
    @Id
    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Id
    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Id
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String ciphertext;

    @Column(name = "iv", nullable = false, length = 16)
    private String iv;

    protected ChatMessage() {
    }

    public ChatMessage(UUID senderId, UUID receiverId, String ciphertext, String iv, Instant timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.ciphertext = ciphertext;
        this.iv = iv;
        this.timestamp = timestamp;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getReceiverId() {
        return receiverId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public String getIv() {
        return iv;
    }

    public static class MessageKey implements Serializable {
        private UUID senderId;
        private UUID receiverId;
        private Instant timestamp;

        public MessageKey() {
        }

        public MessageKey(UUID senderId, UUID receiverId, Instant timestamp) {
            this.senderId = senderId;
            this.receiverId = receiverId;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MessageKey that)) {
                return false;
            }
            return Objects.equals(senderId, that.senderId)
                    && Objects.equals(receiverId, that.receiverId)
                    && Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(senderId, receiverId, timestamp);
        }
    }
}
