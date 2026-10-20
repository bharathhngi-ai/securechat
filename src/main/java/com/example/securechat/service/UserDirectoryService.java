package com.example.securechat.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.securechat.dto.RegisterUserRequest;
import com.example.securechat.entity.User;
import com.example.securechat.repository.UserRepository;

@Service
public class UserDirectoryService {
    private final UserRepository userRepository;
    private final PublicKeyService publicKeyService;

    public UserDirectoryService(UserRepository userRepository, PublicKeyService publicKeyService) {
        this.userRepository = userRepository;
        this.publicKeyService = publicKeyService;
    }

    @Transactional
    public User register(RegisterUserRequest request) {
        publicKeyService.parseAndValidateRsaKey(request.encryptionPublicKey());
        publicKeyService.parseAndValidateRsaKey(request.signingPublicKey());

        return userRepository.findById(request.userId())
                .map(existing -> {
                    if (!existing.getEncryptionPublicKey().equals(request.encryptionPublicKey())
                            || !existing.getSigningPublicKey().equals(request.signingPublicKey())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "This random user ID is already registered to different keys.");
                    }
                    return existing;
                })
                .orElseGet(() -> userRepository.save(new User(
                        request.userId(), request.encryptionPublicKey(), request.signingPublicKey())));
    }
}
