package com.hackathon.agent.gemini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hackathon.agent.memory.Message;
import com.hackathon.agent.memory.Part;
import com.hackathon.agent.tools.Tool;

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
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

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
        return sendWithRetry(history, tools, true);
    }

    private Message sendWithRetry(List<Message> history, List<Tool> tools, boolean allowRetry) throws GeminiException {
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
            if (allowRetry) {
                sleepBriefly();
                return sendWithRetry(history, tools, false);
            }
            throw new GeminiException(
                    "네트워크 연결에 문제가 있는 것 같아요. 연결 상태를 확인하고 다시 시도해주세요.",
                    "네트워크 오류: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new GeminiException(
                    "오늘 사용 가능한 요청 횟수를 다 썼어요. 내일 다시 시도하거나 다른 API 키를 사용해주세요.",
                    "429: " + response.body());
        }
        if (status == 503 || status == 502 || status == 504) {
            if (allowRetry) {
                sleepBriefly();
                return sendWithRetry(history, tools, false);
            }
            throw new GeminiException(
                    "지금 서버가 일시적으로 붐비는 것 같아요. 잠시 후 다시 시도해주세요.",
                    status + ": " + response.body());
        }
        if (status == 400 || status == 401 || status == 403 || status == 404) {
            throw new GeminiException(
                    "요청에 문제가 있어요. API 키와 모델 이름이 올바른지 확인해주세요.",
                    status + ": " + response.body());
        }
        if (status != 200) {
            throw new GeminiException(
                    "알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해주세요.",
                    status + ": " + response.body());
        }

        return parseResponse(response.body());
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
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
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (Exception e) {
            throw new GeminiException(
                    "응답을 이해하지 못했어요. 잠시 후 다시 시도해주세요.",
                    "JSON 파싱 실패: " + responseBody, e);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("알 수 없음");
            throw new GeminiException(
                    "이 요청에는 답변을 만들 수 없었어요 (사유: " + blockReason + "). 다른 방식으로 다시 물어봐 주세요.",
                    "candidates 없음: " + responseBody);
        }

        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText("");
        if (!finishReason.isEmpty() && !finishReason.equals("STOP")) {
            throw new GeminiException(
                    "이 요청에는 답변을 끝까지 만들지 못했어요 (사유: " + finishReason + "). 다른 방식으로 다시 물어봐 주세요.",
                    "finishReason=" + finishReason + ", body=" + responseBody);
        }

        try {
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

            if (parts.isEmpty()) {
                throw new GeminiException(
                        "빈 응답을 받았어요. 다시 시도해주세요.",
                        "parts 없음: " + responseBody);
            }

            return new Message(role, parts);
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException(
                    "응답을 이해하지 못했어요. 잠시 후 다시 시도해주세요.",
                    "Gemini 응답 파싱 실패: " + responseBody, e);
        }
    }

    /** Gemini 호출 실패를 나타내는 예외. 호출부가 값으로 다뤄서 CLI가 죽지 않도록 한다. */
    public static class GeminiException extends Exception {
        private final String userMessage;

        public GeminiException(String userMessage, String debugDetail) {
            super(debugDetail);
            this.userMessage = userMessage;
        }

        public GeminiException(String userMessage, String debugDetail, Throwable cause) {
            super(debugDetail, cause);
            this.userMessage = userMessage;
        }

        /** 사용자에게 그대로 보여줘도 되는 짧은 한국어 메시지. */
        public String userMessage() {
            return userMessage;
        }
    }
}
