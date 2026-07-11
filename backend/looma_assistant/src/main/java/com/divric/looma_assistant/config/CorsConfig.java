package com.divric.looma_assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CorsConfig — enables cross-origin requests from the Vue.js frontend.
 * <p>
 * Allows the local dev server (localhost:5173) and the production Vercel
 * deployment to call the REST API without browser CORS errors.
 */
@Configuration
public class CorsConfig {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";
    private static final String VERCEL_ORIGIN = "https://looma-assistant.vercel.app";

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(LOCAL_ORIGIN, VERCEL_ORIGIN)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}