package com.claySoftware.MathExpAssistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // For all routes development
                .allowedOrigins("*") // Localhost for development
                .allowedMethods("*") // Allowed all http methods for development
                .allowedHeaders("*") ;// Allowed all header for development
    }
}
