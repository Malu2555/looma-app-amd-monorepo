package com.divric.looma_assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AppSecurityInterceptor — a {@link ClientHttpRequestInterceptor} that performs
 * silent authentication by injecting the Fireworks API key as a Bearer token
 * into the {@code Authorization} header of every outbound HTTP request made
 * through Spring's {@code RestClient} / {@code RestTemplate}.
 * <p>
 * This keeps the API key out of individual service calls and centralizes
 * credential handling. The key is read from {@code spring.ai.openai.api-key}
 * (the same property the OpenAI starter uses) so a single config value drives
 * both the chat model and raw HTTP calls to Fireworks.
 */
@Component
public class AppSecurityInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AppSecurityInterceptor.class);

    private final String fireworksApiKey;

    public AppSecurityInterceptor(@Value("${spring.ai.openai.api-key}") String fireworksApiKey) {
        this.fireworksApiKey = fireworksApiKey;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {

        // Silent auth: attach Bearer token to every outbound request
        String bearer = fireworksApiKey != null && !fireworksApiKey.isBlank()
                ? fireworksApiKey
                : "";
        request.getHeaders().setBearerAuth(bearer);

        if (log.isDebugEnabled()) {
            log.debug("AppSecurityInterceptor: attached Bearer token to {} {}",
                    request.getMethod(), request.getURI());
        }

        return execution.execute(request, body);
    }
}