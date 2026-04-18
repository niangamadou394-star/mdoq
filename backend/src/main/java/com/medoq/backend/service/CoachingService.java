package com.medoq.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medoq.backend.controller.CoachingController.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoachingService {

    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL      = "claude-sonnet-4-6";
    private static final String FALLBACK   =
        "Le service de coaching est temporairement indisponible. " +
        "Contacte directement Amadou Niang pour un accompagnement personnalisé ! [SHOW_CTA]";

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private final RestTemplate  restTemplate;
    private final ObjectMapper  objectMapper;

    public String chat(String systemPrompt, List<Message> history, boolean isInit) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Anthropic API key not configured — returning fallback message");
            return FALLBACK;
        }

        try {
            List<Map<String, String>> apiMessages = buildMessages(history, isInit);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      MODEL);
            body.put("max_tokens", 600);
            body.put("system",     systemPrompt);
            body.put("messages",   apiMessages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key",           apiKey);
            headers.set("anthropic-version",   "2023-06-01");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(CLAUDE_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            log.error("Claude API error", e);
            return "Une petite erreur est survenue 😅 Réessaie dans quelques secondes.";
        }
    }

    private List<Map<String, String>> buildMessages(List<Message> history, boolean isInit) {
        List<Map<String, String>> result = new ArrayList<>();

        for (Message m : history) {
            result.add(Map.of("role", m.role(), "content", m.content()));
        }

        if (isInit) {
            result.add(Map.of("role", "user", "content", "Bonjour, je veux commencer mon coaching."));
        }

        return result;
    }
}
