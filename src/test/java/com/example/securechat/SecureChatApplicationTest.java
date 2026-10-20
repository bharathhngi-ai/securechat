package com.example.securechat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.securechat.dto.EncryptedMessageRequest;
import com.example.securechat.entity.ChatMessage;
import com.example.securechat.repository.ChatMessageRepository;
import com.example.securechat.service.MessageService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securechat;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SecureChatApplicationTest {
    @Autowired
    private MessageService messageService;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    void contextLoadsWithMessageServiceAndRepository() {
        assertThat(messageService).isNotNull();
        assertThat(messageRepository).isNotNull();
    }

    @Test
    void messageServicePersistsTheOpaqueRelayFields() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        String ciphertext = "{\"wrappedKey\":\"opaque\",\"encryptedData\":\"opaque\",\"signature\":\"opaque\"}";

        ChatMessage saved = messageService.saveMessage(new EncryptedMessageRequest(
                sender, receiver, "AAAAAAAAAAAAAAAA", ciphertext));

        assertThat(saved.getSenderId()).isEqualTo(sender);
        assertThat(saved.getReceiverId()).isEqualTo(receiver);
        assertThat(saved.getIv()).isEqualTo("AAAAAAAAAAAAAAAA");
        assertThat(saved.getCiphertext()).isEqualTo(ciphertext);
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(messageRepository.findById(
                new ChatMessage.MessageKey(sender, receiver, saved.getTimestamp())))
                .get()
                .extracting(ChatMessage::getCiphertext)
                .isEqualTo(ciphertext);
    }
}
