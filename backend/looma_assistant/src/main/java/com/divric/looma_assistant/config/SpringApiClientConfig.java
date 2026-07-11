package com.divric.looma_assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringApiClientConfig — reads connection settings from the active profile's
 * application-{profile}.yml (dev/prod) and exposes them as beans.
 * <p>
 * The {@link OpenAiChatModel} is auto-configured by the
 * {@code spring-ai-starter-model-openai} starter from the
 * {@code spring.ai.openai.*} properties defined in the YAML files.
 * <p>
 * This config exposes the allowed-models and base URL as beans so that
 * the actor system and routing layer can reference them programmatically.
 * The auto-configured {@code OpenAiChatModel} bean is injected into
 * {@code ActorBridgeService} and passed down to all specialised workers
 * for real LLM inference.
 */
@Configuration
public class SpringApiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringApiClientConfig.class);

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.allowed-models}")
    private String allowedModels;

    /**
     * Exposes the allowed-models string as a bean for the actor routing layer.
     * Workers can inspect this to know which model identifiers are permitted.
     *
     * @return raw allowed-models value from configuration
     *         (e.g. "models/gemma-2b-it,models/gemma-7b-it")
     */
    @Bean
    public String allowedModels() {
        log.debug("Exposing allowedModels bean: {}", allowedModels);
        return allowedModels;
    }

    /**
     * Exposes the configured base URL for diagnostic / health-check purposes.
     */
    @Bean
    public String configuredBaseUrl() {
        log.debug("Exposing configuredBaseUrl bean: {}", baseUrl);
        return baseUrl;
    }
}