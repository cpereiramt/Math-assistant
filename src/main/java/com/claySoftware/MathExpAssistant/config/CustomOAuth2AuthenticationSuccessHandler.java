package com.claySoftware.MathExpAssistant.config;

import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String DEFAULT_REDIRECT_URL = "mathassistant://";

    public CustomOAuth2AuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, IOException {
        // Crie o token JWT com base na autenticação
        String token = jwtTokenProvider.createToken(authentication);

        // Construa a URL de redirecionamento com o token como parâmetro
        String redirectUrl = DEFAULT_REDIRECT_URL + "?token=" + token;

        // Redirecione para o app com as informações
        response.sendRedirect(redirectUrl);
    }
}