package com.example.securechat.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securechat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, ChatMessage.MessageKey> {
    boolean existsBySenderIdAndReceiverIdAndTimestamp(UUID senderId, UUID receiverId, Instant timestamp);
}
