package com.claySoftware.MathExpAssistant.config;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.services.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;

import java.io.IOException;
import org.springframework.core.env.Environment;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final Environment environment;

    public OAuth2LoginSuccessHandler(JwtTokenProvider jwtTokenProvider, Environment environment) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.environment = environment;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String token = null;
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            token = jwtTokenProvider.createToken(authentication);
        }
        String FRONTEND_BASE_URL = this.environment.getProperty("FRONTEND_BASE_URL");

        response.setStatus(HttpServletResponse.SC_OK);
        response.sendRedirect(FRONTEND_BASE_URL + "/auth/callback?token=" + token); // ajuste conforme seu fluxo
    }
}
