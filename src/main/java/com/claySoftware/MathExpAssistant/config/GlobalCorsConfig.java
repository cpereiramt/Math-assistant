package com.claySoftware.MathExpAssistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // For all routes development
                .allowedOrigins("https://math-assistant.claytonpereira.com") // Localhost for development
                .allowedMethods("GET, POST, PUT, DELETE", "OPTIONS") // Allowed all http methods for development
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin")
                .allowCredentials(true); 
    }
}
