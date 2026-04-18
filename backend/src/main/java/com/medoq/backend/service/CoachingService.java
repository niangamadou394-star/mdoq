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

    // OpenRouter API — compatible avec Claude et autres modèles
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL          = "anthropic/claude-3-5-sonnet";
    private static final String FALLBACK       =
        "Le service de coaching est temporairement indisponible. " +
        "Contacte directement Amadou Niang pour un accompagnement personnalisé ! [SHOW_CTA]";

    @Value("${openrouter.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String chat(String systemPrompt, List<Message> history, boolean isInit) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenRouter API key not configured — returning fallback");
            return FALLBACK;
        }

        try {
            List<Map<String, String>> messages = buildMessages(systemPrompt, history, isInit);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      MODEL);
            body.put("max_tokens", 600);
            body.put("messages",   messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer",  "https://moncoach.amadouniang.com");
            headers.set("X-Title",       "MonCoach par Amadou Niang");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENROUTER_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("OpenRouter API error", e);
            return "Une petite erreur est survenue 😅 Réessaie dans quelques secondes.";
        }
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, List<Message> history, boolean isInit) {
        List<Map<String, String>> result = new ArrayList<>();

        // OpenRouter/OpenAI format : system message en premier
        result.add(Map.of("role", "system", "content", systemPrompt));

        for (Message m : history) {
            result.add(Map.of("role", m.role(), "content", m.content()));
        }

        if (isInit) {
            result.add(Map.of("role", "user", "content", "Bonjour, je veux commencer mon coaching."));
        }

        return result;
    }
}
