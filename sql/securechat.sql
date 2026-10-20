CREATE DATABASE IF NOT EXISTS securechat
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE securechat;

CREATE TABLE IF NOT EXISTS blind_users (
    user_id BINARY(16) NOT NULL,
    encryption_public_key VARCHAR(2000) NOT NULL,
    signing_public_key VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS encrypted_messages (
    sender_id BINARY(16) NOT NULL,
    receiver_id BINARY(16) NOT NULL,
    `timestamp` DATETIME(6) NOT NULL,
    ciphertext MEDIUMTEXT NOT NULL,
    iv VARCHAR(16) NOT NULL,
    PRIMARY KEY (sender_id, receiver_id, `timestamp`),
    INDEX idx_message_receiver (receiver_id, `timestamp`),
    CONSTRAINT fk_message_sender
        FOREIGN KEY (sender_id) REFERENCES blind_users (user_id),
    CONSTRAINT fk_message_receiver
        FOREIGN KEY (receiver_id) REFERENCES blind_users (user_id)
) ENGINE=InnoDB;
