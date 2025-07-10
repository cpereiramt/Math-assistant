package com.claySoftware.MathExpAssistant.config;

import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String DEFAULT_REDIRECT_URL;

    private final JwtTokenProvider jwtTokenProvider;
    // private static final String DEFAULT_REDIRECT_URL = "mathassistant://"; redirect for mobile

    public CustomOAuth2AuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, IOException {
        // Create the token for authentication
        String token = jwtTokenProvider.createToken(authentication);

        // build the redirectURL
        String redirectUrl = DEFAULT_REDIRECT_URL + "?token=" + token;
        System.out.print("token ------> "+ token);

        // Redirect to the mobile app or to web app
        response.sendRedirect(redirectUrl);
    }
}