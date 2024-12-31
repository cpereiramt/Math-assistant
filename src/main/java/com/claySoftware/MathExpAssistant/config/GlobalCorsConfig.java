package com.claySoftware.MathExpAssistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Aplica a todas as rotas
                .allowedOrigins("*") // Permite localhost para desenvolvimento
                .allowedMethods("*") // Permite todos os métodos HTTP
                .allowedHeaders("*") ;// Permite todos os cabeçalhos
    }
}
