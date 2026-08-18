package com.hackathon.agent;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 사용 가능한 도구들을 이름으로 찾아 실행한다. */
public class ToolRegistry {

    private final Map<String, Tool> byName;

    public ToolRegistry(List<Tool> tools) {
        this.byName = tools.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
    }

    public List<Tool> all() {
        return List.copyOf(byName.values());
    }

    public ToolResult execute(String name, Map<String, Object> args) {
        Tool tool = byName.get(name);
        if (tool == null) {
            return ToolResult.error("등록되지 않은 도구입니다: " + name);
        }
        return tool.execute(args);
    }
}
