package com.example.securechat.config;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.securechat.entity.User;
import com.example.securechat.repository.UserRepository;
import com.example.securechat.service.ChallengeService;
import com.example.securechat.service.ClientSignatureVerifier;

@Component
public class ChatWebSocketInterceptor implements ChannelInterceptor {
    private final UserRepository userRepository;
    private final ChallengeService challengeService;
    private final ClientSignatureVerifier signatureVerifier;

    public ChatWebSocketInterceptor(UserRepository userRepository, ChallengeService challengeService,
            ClientSignatureVerifier signatureVerifier) {
        this.userRepository = userRepository;
        this.challengeService = challengeService;
        this.signatureVerifier = signatureVerifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnect(accessor);
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        } else if (accessor.getCommand() == StompCommand.SEND) {
            if (!"/app/chat.send".equals(accessor.getDestination()) || accessor.getUser() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only authenticated chat sends are allowed.");
            }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        try {
            if (accessor.getUser() != null) {
                throw new IllegalArgumentException("The STOMP session is already authenticated.");
            }
            UUID userId = UUID.fromString(requiredHeader(accessor, "x-user-id"));
            String nonce = requiredHeader(accessor, "x-nonce");
            String signature = requiredHeader(accessor, "x-signature");
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown user ID."));
            if (!challengeService.consume(userId, nonce)
                    || !signatureVerifier.verifyConnection(user, nonce, signature)) {
                throw new IllegalArgumentException("Invalid or expired connection proof.");
            }
            Principal principal = () -> userId.toString();
            accessor.setUser(principal);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WebSocket authentication failed.");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || !"/user/queue/messages".equals(accessor.getDestination())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to subscribe to this destination.");
        }
    }

    private String requiredHeader(StompHeaderAccessor accessor, String name) {
        String value = accessor.getFirstNativeHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing authentication header.");
        }
        return value;
    }
}
