package com.claySoftware.MathExpAssistant.controllers;


import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class GoogleOauthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    @Value("${spring.security.oauth2.client.provider.google.authorization-uri}")
    private String authorizationUri;

    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String tokenUri;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String userInfoUri;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = authorizationUri + "?response_type=code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&scope=profile email";

        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
    }

    @PostMapping("/callback")
    public void  callback(@RequestParam String code, HttpServletResponse response) throws IOException {
        RestTemplate restTemplate = new RestTemplate();

        // Criar a requisição para trocar o código pelo token
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUri, request, Map.class);

        if (tokenResponse.getStatusCode() == HttpStatus.OK) {
            // Inclua o token no redirecionamento de volta ao app
            String token = (String) tokenResponse.getBody().get("access_token");
            String redirectUrl = redirectUri + "?token=" + token;
            response.sendRedirect(redirectUrl);
        } else {
            response.sendRedirect(redirectUri + "?error=auth_failed");
        }
    }

    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String token) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.replace("Bearer ", ""));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return ResponseEntity.ok(response.getBody());
        } else {
            return ResponseEntity.status(response.getStatusCode()).body("Error on query user information.");
        }
    }
}