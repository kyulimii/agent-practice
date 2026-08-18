package com.hackathon.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gemini generateContent REST API를 호출한다 (텍스트 + 함수 호출 프로토콜). */
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

    /** 대화 이력 전체와 사용 가능한 도구를 보내고, 모델의 응답 턴(텍스트 또는 함수 호출)을 받는다. */
    public Message send(List<Message> history, List<Tool> tools) throws GeminiException {
        String url = ENDPOINT.formatted(model, apiKey);
        String body = buildRequestBody(history, tools);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
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

        return parseResponse(response.body());
    }

    private String buildRequestBody(List<Message> history, List<Tool> tools) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        for (Message m : history) {
            ObjectNode turn = contents.addObject();
            turn.put("role", m.role());
            ArrayNode parts = turn.putArray("parts");
            for (Part p : m.parts()) {
                writePart(parts.addObject(), p);
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ObjectNode toolsEntry = root.putArray("tools").addObject();
            ArrayNode declarations = toolsEntry.putArray("functionDeclarations");
            for (Tool t : tools) {
                declarations.add(t.declaration());
            }
        }

        return root.toString();
    }

    private void writePart(ObjectNode node, Part p) {
        switch (p) {
            case Part.Text t -> {
                node.put("text", t.text());
                if (t.thoughtSignature() != null) {
                    node.put("thoughtSignature", t.thoughtSignature());
                }
            }
            case Part.FunctionCall fc -> {
                ObjectNode call = node.putObject("functionCall");
                call.put("name", fc.name());
                if (fc.id() != null) {
                    call.put("id", fc.id());
                }
                call.set("args", mapper.valueToTree(fc.args()));
                if (fc.thoughtSignature() != null) {
                    node.put("thoughtSignature", fc.thoughtSignature());
                }
            }
            case Part.FunctionResponse fr -> {
                ObjectNode resp = node.putObject("functionResponse");
                resp.put("name", fr.name());
                if (fr.id() != null) {
                    resp.put("id", fr.id());
                }
                resp.set("response", mapper.valueToTree(fr.response()));
            }
        }
    }

    private Message parseResponse(String responseBody) throws GeminiException {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode candidate = root.path("candidates").get(0);
            if (candidate == null) {
                throw new GeminiException("Gemini 응답에 candidates가 없습니다: " + responseBody);
            }
            JsonNode content = candidate.path("content");
            String role = content.path("role").asText("model");

            List<Part> parts = new ArrayList<>();
            for (JsonNode partNode : content.path("parts")) {
                String thoughtSignature = partNode.has("thoughtSignature")
                        ? partNode.get("thoughtSignature").asText()
                        : null;

                if (partNode.has("functionCall")) {
                    JsonNode fc = partNode.get("functionCall");
                    String name = fc.path("name").asText();
                    String id = fc.has("id") ? fc.get("id").asText() : null;
                    Map<String, Object> args = mapper.convertValue(fc.path("args"), new TypeReference<>() {
                    });
                    parts.add(new Part.FunctionCall(name, id, args, thoughtSignature));
                } else if (partNode.has("text")) {
                    parts.add(new Part.Text(partNode.get("text").asText(), thoughtSignature));
                }
            }

            return new Message(role, parts);
        } catch (GeminiException e) {
            throw e;
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
