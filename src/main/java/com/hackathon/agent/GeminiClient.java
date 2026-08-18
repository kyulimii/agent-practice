package com.hackathon.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Gemini generateContent REST API를 호출한다. */
public class GeminiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 대화 이력 전체를 보내고 모델의 답변 텍스트를 반환한다. */
    public String send(List<Message> history) throws GeminiException {
        String url = ENDPOINT.formatted(model, apiKey);
        String body = buildRequestBody(history);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new GeminiException("Gemini API 호출 중 네트워크 오류: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new GeminiException(
                    "Gemini API가 오류를 반환했습니다 (status=%d): %s"
                            .formatted(response.statusCode(), response.body()));
        }

        return extractText(response.body());
    }

    private String buildRequestBody(List<Message> history) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        for (Message m : history) {
            ObjectNode turn = contents.addObject();
            turn.put("role", m.role());
            ArrayNode parts = turn.putArray("parts");
            parts.addObject().put("text", m.content());
        }

        return root.toString();
    }

    private String extractText(String responseBody) throws GeminiException {
        try {
            JsonNode root = mapper.readTree(responseBody);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw new GeminiException("Gemini 응답을 해석할 수 없습니다: " + responseBody, e);
        }
    }

    /** Gemini 호출 실패를 나타내는 예외. 호출부가 값으로 다뤄서 CLI가 죽지 않도록 한다. */
    public static class GeminiException extends Exception {
        public GeminiException(String message) {
            super(message);
        }

        public GeminiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
