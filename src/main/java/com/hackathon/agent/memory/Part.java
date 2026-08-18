package com.hackathon.agent.memory;

import java.util.Map;

/**
 * Gemini의 Content.parts 한 조각. functionCall/functionResponse는 함수 호출 프로토콜에 쓰인다.
 * thoughtSignature는 모델이 붙여준 값을 그대로 되돌려줘야(echo) 후속 턴에서 오류가 나지 않는다.
 */
public sealed interface Part {

    record Text(String text, String thoughtSignature) implements Part {
        public Text(String text) {
            this(text, null);
        }
    }

    record FunctionCall(String name, String id, Map<String, Object> args, String thoughtSignature) implements Part {
    }

    record FunctionResponse(String name, String id, Map<String, Object> response) implements Part {
    }
}
