package com.hackathon.agent.tools;

import java.util.Map;

/** 도구 실행 결과. 실패도 예외 대신 값으로 돌려줘서 에이전트가 실패 이유를 알 수 있게 한다. */
public record ToolResult(boolean ok, Map<String, Object> data, String error) {

    public static ToolResult ok(Map<String, Object> data) {
        return new ToolResult(true, data, null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, null, message);
    }

    /** Gemini에 돌려줄 functionResponse.response 페이로드. */
    public Map<String, Object> asResponsePayload() {
        return ok ? data : Map.of("error", error);
    }
}
