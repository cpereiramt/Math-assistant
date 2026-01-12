package com.claySoftware.MathExpAssistant.controllers;

import java.util.Map;

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
public Map<String, Object> token(Authentication authentication) {
String jwt = jwtTokenProvider.createToken(authentication);
return Map.of(
"tokenType", "Bearer",
"accessToken", jwt,
"expiresIn", 3600
);
}
}