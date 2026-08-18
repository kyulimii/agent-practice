package com.hackathon.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** 모델이 스스로 고를 수 있는 도구 하나. */
public interface Tool {

    String name();

    /** Gemini의 functionDeclarations 항목 하나 (name, description, parameters). */
    ObjectNode declaration();

    ToolResult execute(Map<String, Object> args);
}
