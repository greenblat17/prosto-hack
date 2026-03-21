package com.prosto.analytics.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class AiClient {

    public record Message(String role, String content) {}

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final JsonMapper objectMapper;
    private final HttpClient httpClient;
    /** OpenRouter: рекомендуется для атрибуции и избегания 403 в части сценариев */
    private final Optional<String> httpReferer;
    private final Optional<String> appTitle;

    public AiClient(String apiUrl, String apiKey, String model, double temperature, JsonMapper objectMapper,
                    String httpReferer, String appTitle) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.httpReferer = Optional.ofNullable(httpReferer).filter(s -> !s.isBlank());
        this.appTitle = Optional.ofNullable(appTitle).filter(s -> !s.isBlank());
    }

    public String chatWithHistory(String systemPrompt, java.util.List<Message> history, String userPrompt) {
        int maxRetries = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doChatWithHistory(systemPrompt, history, userPrompt);
            } catch (Exception e) {
                lastError = e;
                log.warn("AI attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("AI запрос прерван", ie);
                    }
                }
            }
        }
        throw new RuntimeException("AI не отвечает после " + maxRetries + " попыток", lastError);
    }

    public String chat(String systemPrompt, String userPrompt) {
        int maxRetries = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doChat(systemPrompt, userPrompt);
            } catch (Exception e) {
                lastError = e;
                log.warn("AI attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("AI запрос прерван", ie);
                    }
                }
            }
        }
        throw new RuntimeException("AI не отвечает после " + maxRetries + " попыток", lastError);
    }

    private String doChatWithHistory(String systemPrompt, java.util.List<Message> history, String userPrompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }
        for (Message m : history) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            msg.put("content", m.content());
        }
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        HttpRequest request = buildChatRequest(objectMapper.writeValueAsString(body));

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("AI API error: status={} body={}", response.statusCode(), response.body());
            throw new RuntimeException("AI API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private String doChat(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        HttpRequest request = buildChatRequest(objectMapper.writeValueAsString(body));

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("AI API error: status={} body={}", response.statusCode(), response.body());
            throw new RuntimeException("AI API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private HttpRequest buildChatRequest(String jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60));
        httpReferer.ifPresent(ref -> b.header("HTTP-Referer", ref));
        appTitle.ifPresent(title -> b.header("X-OpenRouter-Title", title));
        return b.build();
    }
}
