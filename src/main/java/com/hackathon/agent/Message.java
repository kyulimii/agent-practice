package com.hackathon.agent;

import java.util.List;

/** 대화 이력 한 턴. role은 "user" 또는 "model". */
public record Message(String role, List<Part> parts) {

    public static Message userText(String text) {
        return new Message("user", List.of(new Part.Text(text)));
    }

    public static Message functionResponse(String name, String id, java.util.Map<String, Object> response) {
        return new Message("user", List.of(new Part.FunctionResponse(name, id, response)));
    }

    /** 첫 텍스트 파트를 사람이 읽을 문자열로 뽑아낸다. 텍스트 파트가 없으면 빈 문자열. */
    public String textOrEmpty() {
        return parts.stream()
                .filter(p -> p instanceof Part.Text)
                .map(p -> ((Part.Text) p).text())
                .findFirst()
                .orElse("");
    }

    public List<Part.FunctionCall> functionCalls() {
        return parts.stream()
                .filter(p -> p instanceof Part.FunctionCall)
                .map(p -> (Part.FunctionCall) p)
                .toList();
    }
}
