package com.claySoftware.MathExpAssistant.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.utils.AdminBypass;

import java.util.Date;
import java.util.Map;

@Service
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private final AdminBypass adminBypass;
    private final UserService userService;

    public JwtTokenProvider(AdminBypass adminBypass, UserService userService) {
        this.adminBypass = adminBypass;
        this.userService = userService;
    }

    public String createToken(Authentication authentication) {
        String sub;
        String username;
        String email;
        String pictureUrl;
        if (authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            sub = oidcUser.getSubject();
            username = oidcUser.getName();
            email = oidcUser.getEmail();
            Map<String, Object> claims = oidcUser.getClaims();
            pictureUrl = claims.get("picture") != null ? claims.get("picture").toString() : null;
        } else {
            throw new IllegalArgumentException(
                    "Unexpected principal type: " + authentication.getPrincipal().getClass());
        }
        UserPlan effectivePlan = adminBypass.effectivePlan(email);

        userService.upsertOAuthUser(sub, email, username, pictureUrl, effectivePlan.name());

        return Jwts.builder()
                .setSubject(username)
                .claim("email", email)
                .claim("plan", effectivePlan.name())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    public String getJwtSecret() {
        return jwtSecret;
    }
}