package com.example.securechat.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.securechat.dto.EncryptedMessageRequest;
import com.example.securechat.entity.ChatMessage;
import com.example.securechat.repository.ChatMessageRepository;

@Service
public class MessageServiceImpl implements MessageService {
    private final ChatMessageRepository messageRepository;

    public MessageServiceImpl(ChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public synchronized ChatMessage saveMessage(EncryptedMessageRequest request) {
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        while (messageRepository.existsBySenderIdAndReceiverIdAndTimestamp(
                request.senderId(), request.receiverId(), timestamp)) {
            timestamp = timestamp.plus(1, ChronoUnit.MICROS);
        }
        return messageRepository.save(new ChatMessage(
                request.senderId(), request.receiverId(), request.ciphertext(), request.iv(), timestamp));
    }
}
