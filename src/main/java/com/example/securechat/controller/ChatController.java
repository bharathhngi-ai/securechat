package com.example.securechat.controller;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import com.example.securechat.dto.EncryptedMessageRequest;
import com.example.securechat.dto.RelayedMessage;
import com.example.securechat.entity.ChatMessage;
import com.example.securechat.repository.UserRepository;
import com.example.securechat.service.MessageService;

@Controller
public class ChatController {
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final UserRepository userRepository;

    public ChatController(SimpMessagingTemplate messagingTemplate, MessageService messageService,
            UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    @MessageMapping("/chat.send")
    public void sendEncryptedMessage(@Valid EncryptedMessageRequest request, Principal principal) {
        UUID authenticatedSender = UUID.fromString(principal.getName());
        if (!authenticatedSender.equals(request.senderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sender does not match the authenticated session.");
        }
        if (!userRepository.existsById(request.receiverId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient blind ID.");
        }

        ChatMessage saved = messageService.saveMessage(request);
        RelayedMessage relayPacket = new RelayedMessage(
                saved.getSenderId(), saved.getReceiverId(), saved.getIv(),
                saved.getCiphertext(), saved.getTimestamp());
        messagingTemplate.convertAndSendToUser(
                saved.getReceiverId().toString(), "/queue/messages", relayPacket);
    }
}
