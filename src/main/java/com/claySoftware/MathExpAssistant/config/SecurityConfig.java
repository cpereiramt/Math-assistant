package com.claySoftware.MathExpAssistant.config;


import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    private final CustomOAuth2AuthenticationSuccessHandler successHandler;


    public SecurityConfig(JwtTokenProvider jwtTokenProvider, CustomOAuth2AuthenticationSuccessHandler successHandler) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.successHandler = successHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/login", "/error").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oauth2Login -> {
                    oauth2Login
                            .userInfoEndpoint(userInfoEndpoint ->
                                    userInfoEndpoint.oidcUserService(oidcUserService())
                            )
                            .successHandler(successHandler);
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }


    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }
}