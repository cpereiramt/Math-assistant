package com.claySoftware.MathExpAssistant.config;

import com.claySoftware.MathExpAssistant.services.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final JwtTokenProvider jwtTokenProvider;

        public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
                this.jwtTokenProvider = jwtTokenProvider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                        ApiAuthenticationEntryPoint apiEntryPoint,
                        OAuth2LoginSuccessHandler successHandler) throws Exception {

                http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .authorizeHttpRequests(auth -> auth
                                         .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                // Swagger + OpenAPI
                                                .requestMatchers(
                                                                "/docs/**",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()

                                                // Actuator
                                                .requestMatchers("/actuator/**").permitAll()

                                                // OAuth2 endpoints internos do Spring
                                                .requestMatchers(
                                                                "/oauth2/**",
                                                                "/login/oauth2/**",
                                                                "/error")
                                                .permitAll()

                                                // Emissão de JWT (exige login Google)
                                                .requestMatchers("/auth/token").authenticated()

                                                // API protegida por JWT
                                                .requestMatchers("/api/**").authenticated()

                                                .anyRequest().denyAll())

                                .exceptionHandling(ex -> ex
                                                .defaultAuthenticationEntryPointFor(
                                                                apiEntryPoint,
                                                                new AntPathRequestMatcher("/auth/token"))
                                                        
                                                  .defaultAuthenticationEntryPointFor(
                                                        apiEntryPoint,                                                        
                                                        new AntPathRequestMatcher("/api/**")))
                                .oauth2Login(oauth2 -> oauth2.successHandler(successHandler));

                http.addFilterBefore(
                                new JwtAuthenticationFilter(jwtTokenProvider),
                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://math-assistant.claytonpereira.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
