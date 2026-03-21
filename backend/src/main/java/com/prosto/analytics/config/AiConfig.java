package com.prosto.analytics.config;

import tools.jackson.databind.json.JsonMapper;
import com.prosto.analytics.service.GigaChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Value("${ai.api-url:}")
    private String apiUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:}")
    private String model;

    @Value("${ai.temperature:0.2}")
    private double temperature;

    @Bean
    public GigaChatClient gigaChatClient(JsonMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API key not configured (AI_API_KEY) — AI features will use fallback responses");
            return null;
        }
        if (apiUrl == null || apiUrl.isBlank() || (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://"))) {
            log.warn("AI API URL invalid or missing (AI_API_URL='{}') — AI features will use fallback responses", apiUrl);
            return null;
        }
        if (model == null || model.isBlank()) {
            log.warn("AI model not configured (AI_MODEL) — AI features will use fallback responses");
            return null;
        }
        log.info("AI configured: url={} model={}", apiUrl, model);
        return new GigaChatClient(apiUrl, apiKey, model, temperature, objectMapper);
    }
}
