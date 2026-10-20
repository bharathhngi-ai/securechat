package com.example.securechat.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securechat.dto.ChallengeResponse;
import com.example.securechat.service.ChallengeService;

@RestController
@RequestMapping("/api/auth")
public class ChallengeController {
    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @GetMapping("/challenge/{userId}")
    public ChallengeResponse issueChallenge(@PathVariable UUID userId) {
        return new ChallengeResponse(challengeService.issue(userId));
    }
}
