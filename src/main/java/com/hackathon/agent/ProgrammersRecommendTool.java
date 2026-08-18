package com.hackathon.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 주제별 프로그래머스 코딩테스트 연습 문제를 추천한다.
 * 프로그래머스는 공개 문제 검색 API가 없어 큐레이션 목록을 쓴다.
 * 아래 제목·레벨은 school.programmers.co.kr에서 직접 조회해 검증한 값이다 (2026-08-18 기준).
 */
public class ProgrammersRecommendTool implements Tool {

    private record Problem(String title, int level, long id) {
        String url() {
            return "https://school.programmers.co.kr/learn/courses/30/lessons/" + id;
        }
    }

    private static final Map<String, List<Problem>> CATALOG = Map.of(
            "hash", List.of(
                    new Problem("완주하지 못한 선수", 1, 42576),
                    new Problem("전화번호 목록", 2, 42577),
                    new Problem("의상", 2, 42578),
                    new Problem("베스트앨범", 3, 42579)
            ),
            "full-search", List.of(
                    new Problem("두 개 뽑아서 더하기", 1, 68644),
                    new Problem("모의고사", 1, 42840),
                    new Problem("소수 찾기", 2, 42839),
                    new Problem("카펫", 2, 42842)
            ),
            "greedy", List.of(
                    new Problem("체육복", 1, 42862),
                    new Problem("큰 수 만들기", 2, 42883),
                    new Problem("구명보트", 2, 42885),
                    new Problem("조이스틱", 2, 42860),
                    new Problem("단속카메라", 3, 42884)
            ),
            "stack-queue", List.of(
                    new Problem("기능개발", 2, 42586),
                    new Problem("프로세스", 2, 42587),
                    new Problem("다리를 지나는 트럭", 2, 42583),
                    new Problem("주식가격", 2, 42584)
            ),
            "dfs-bfs", List.of(
                    new Problem("타겟 넘버", 2, 43165),
                    new Problem("게임 맵 최단거리", 2, 1844),
                    new Problem("네트워크", 3, 43162),
                    new Problem("단어 변환", 3, 43163),
                    new Problem("여행경로", 3, 43164)
            ),
            "sorting", List.of(
                    new Problem("K번째수", 1, 42748),
                    new Problem("가장 큰 수", 2, 42746),
                    new Problem("H-Index", 2, 42747)
            ),
            "binary-search", List.of(
                    new Problem("입국심사", 3, 43238),
                    new Problem("징검다리", 4, 43236)
            ),
            "dynamic-programming", List.of(
                    new Problem("N으로 표현", 3, 42895),
                    new Problem("정수 삼각형", 3, 43105),
                    new Problem("등굣길", 3, 42898),
                    new Problem("도둑질", 4, 42897)
            )
    );

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "recommend_programmers_problems";
    }

    @Override
    public ObjectNode declaration() {
        ObjectNode decl = mapper.createObjectNode();
        decl.put("name", name());
        decl.put("description",
                "주제에 맞는 프로그래머스 코딩테스트 연습 문제를 추천한다 (제목·레벨·링크). "
                        + "사용자가 프로그래머스 문제나 한국어 코딩테스트 연습을 원할 때 사용한다. "
                        + "topic은 정해진 목록(enum) 중에서만 고를 수 있다. 사용자가 말한 주제가 목록의 어떤 topic과도 "
                        + "명확히 일치하지 않으면 이 도구를 호출하지 말고, 일치하는 주제가 없다고 사용자에게 솔직히 말한 뒤 "
                        + "가장 가까운 대안 주제를 제안하라.");

        ObjectNode params = decl.putObject("parameters");
        params.put("type", "OBJECT");
        ObjectNode props = params.putObject("properties");

        ObjectNode topic = props.putObject("topic");
        topic.put("type", "STRING");
        topic.put("description", "문제 주제. 사용자의 자연어 주제를 아래 목록 중 가장 가까운 것으로 매핑한다.");
        ArrayNode enumArr = topic.putArray("enum");
        CATALOG.keySet().forEach(enumArr::add);

        params.putArray("required").add("topic");
        return decl;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Object topicArg = args.get("topic");
        if (topicArg == null || topicArg.toString().isBlank()) {
            return ToolResult.error("topic 파라미터가 필요합니다.");
        }
        String topic = topicArg.toString();
        List<Problem> problems = CATALOG.get(topic);
        if (problems == null) {
            return ToolResult.error("알 수 없는 주제입니다: " + topic + ". 사용 가능한 주제: " + CATALOG.keySet());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Problem p : problems) {
            result.add(Map.of(
                    "title", p.title(),
                    "level", "Lv." + p.level(),
                    "url", p.url()
            ));
        }
        return ToolResult.ok(Map.of("problems", result));
    }
}
