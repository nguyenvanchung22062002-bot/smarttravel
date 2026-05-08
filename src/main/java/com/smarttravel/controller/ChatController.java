package com.smarttravel.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    private static final String MODEL = "gemini-2.5-flash-lite";

    private String getApiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key=" + geminiApiKey;
    }

    private RestTemplate buildRestTemplate() {
        // Dùng Apache HttpClient 5 để có timeout chính xác
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(10_000);

        return new RestTemplate(factory);
    }


    public record ChatRequest(
            @JsonProperty("systemPrompt") String systemPrompt,
            @JsonProperty("messages")     List<Message> messages
    ) {}

    public record Message(
            @JsonProperty("role")    String role,
            @JsonProperty("content") String content
    ) {}

    public record ChatResponse(String reply) {}
    public record ErrorResponse(String message) {}


    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest req) {
        if (req.messages() == null || req.messages().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("messages không được để trống"));
        }

        try {
            // Gemini dùng "contents", "assistant" → đổi thành "model"
            List<Map<String, Object>> contents = new ArrayList<>();
            for (Message msg : req.messages()) {
                String role = "assistant".equals(msg.role()) ? "model" : "user";
                contents.add(Map.of(
                        "role",  role,
                        "parts", List.of(Map.of("text", msg.content()))
                ));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("contents", contents);
            body.put("generationConfig", Map.of(
                    "maxOutputTokens", 400,
                    "temperature",     0.7
            ));

            if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
                body.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", req.systemPrompt()))
                ));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Connection", "keep-alive");

            RestTemplate restTemplate = buildRestTemplate();

            ResponseEntity<Map> response = restTemplate.exchange(
                    getApiUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> respBody = response.getBody();
            if (respBody == null) {
                return ResponseEntity.status(502)
                        .body(new ErrorResponse("Gemini trả về response rỗng"));
            }

            List<?> candidates = (List<?>) respBody.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                Object feedback = respBody.get("promptFeedback");
                String detail = feedback != null ? feedback.toString() : "không có candidates";
                return ResponseEntity.status(502)
                        .body(new ErrorResponse("Gemini không trả về kết quả: " + detail));
            }

            Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content   = (Map<?, ?>) candidate.get("content");
            List<?>   parts     = (List<?>) content.get("parts");
            String    text      = (String) ((Map<?, ?>) parts.get(0)).get("text");

            return ResponseEntity.ok(new ChatResponse(text));

        } catch (Exception e) {
            System.err.println("[ChatController] Lỗi Gemini API: " + e.getMessage());
            return ResponseEntity.status(502)
                    .body(new ErrorResponse("Lỗi kết nối AI: " + e.getMessage()));
        }
    }
}