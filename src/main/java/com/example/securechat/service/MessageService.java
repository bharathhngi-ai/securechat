package com.example.securechat.service;

import com.example.securechat.dto.EncryptedMessageRequest;
import com.example.securechat.entity.ChatMessage;

public interface MessageService {
    ChatMessage saveMessage(EncryptedMessageRequest request);
}
