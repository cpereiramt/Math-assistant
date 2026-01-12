package com.claySoftware.MathExpAssistant.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/token")
    public ResponseEntity<?> token(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "not_authenticated",
                            "message", "Faça login abrindo a URL de login em uma nova aba e depois tente novamente.",
                            "loginUrl", "/oauth2/authorization/google"));
        }
        String jwt = jwtTokenProvider.createToken(authentication);
        return ResponseEntity.ok(Map.of("token", jwt));

    }
}