package com.hackathon.agent.tools;

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

/** 주제 태그로 LeetCode 문제를 실시간 검색한다 (비공식 GraphQL API). */
public class LeetCodeSearchTool implements Tool {

    private static final String ENDPOINT = "https://leetcode.com/graphql";

    private static final List<String> TAGS = List.of(
            "array", "string", "hash-table", "dynamic-programming", "math", "sorting",
            "greedy", "depth-first-search", "breadth-first-search", "binary-search",
            "two-pointers", "stack", "backtracking", "graph", "tree", "linked-list",
            "sliding-window", "heap-priority-queue"
    );

    private static final String QUERY = """
            query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {
              problemsetQuestionList: questionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) {
                total: totalNum
                questions: data {
                  difficulty
                  title
                  titleSlug
                  paidOnly: isPaidOnly
                }
              }
            }""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() {
        return "search_leetcode_problems";
    }

    @Override
    public ObjectNode declaration() {
        ObjectNode decl = mapper.createObjectNode();
        decl.put("name", name());
        decl.put("description",
                "주제(태그)에 맞는 LeetCode 문제를 실시간으로 검색해 제목·난이도·링크를 반환한다. "
                        + "사용자가 LeetCode 문제나 특정 알고리즘 주제의 영어권 코딩테스트 문제를 원할 때 사용한다. "
                        + "tag는 정해진 목록(enum) 중에서만 고를 수 있다. 사용자가 말한 주제가 '시간복잡도', '문법', "
                        + "'디버깅' 처럼 목록의 어떤 태그와도 명확히 일치하지 않으면 이 도구를 호출하지 말고, "
                        + "일치하는 태그가 없다고 사용자에게 솔직히 말한 뒤 가장 가까운 대안 태그를 제안하라.");

        ObjectNode params = decl.putObject("parameters");
        params.put("type", "OBJECT");
        ObjectNode props = params.putObject("properties");

        ObjectNode tag = props.putObject("tag");
        tag.put("type", "STRING");
        tag.put("description", "LeetCode 주제 태그 슬러그. 사용자의 자연어 주제를 아래 목록 중 가장 가까운 것으로 매핑한다.");
        ArrayNode enumArr = tag.putArray("enum");
        TAGS.forEach(enumArr::add);

        ObjectNode count = props.putObject("count");
        count.put("type", "INTEGER");
        count.put("description", "가져올 문제 개수. 기본 5, 최대 10.");

        params.putArray("required").add("tag");
        return decl;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Object tagArg = args.get("tag");
        if (tagArg == null || tagArg.toString().isBlank()) {
            return ToolResult.error("tag 파라미터가 필요합니다.");
        }
        String tag = tagArg.toString();
        if (!TAGS.contains(tag)) {
            return ToolResult.error("알 수 없는 태그입니다: " + tag + ". 사용 가능한 태그: " + TAGS);
        }

        int count = 5;
        Object countArg = args.get("count");
        if (countArg instanceof Number n) {
            count = Math.min(Math.max(n.intValue(), 1), 10);
        }

        try {
            String requestBody = buildRequestBody(tag, count);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (agent-hackathon)")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ToolResult.error("LeetCode API가 오류를 반환했습니다 (status=%d)".formatted(response.statusCode()));
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            return ToolResult.error("LeetCode 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private String buildRequestBody(String tag, int count) {
        ObjectNode root = mapper.createObjectNode();
        root.put("query", QUERY);
        ObjectNode variables = root.putObject("variables");
        variables.put("categorySlug", "");
        variables.put("skip", 0);
        variables.put("limit", count);
        ObjectNode filters = variables.putObject("filters");
        filters.putArray("tags").add(tag);
        return root.toString();
    }

    private ToolResult parseResponse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode questions = root.path("data").path("problemsetQuestionList").path("questions");
            if (!questions.isArray()) {
                return ToolResult.error("LeetCode 응답을 해석할 수 없습니다: " + body);
            }

            List<Map<String, Object>> problems = new ArrayList<>();
            for (JsonNode q : questions) {
                if (q.path("paidOnly").asBoolean(false)) {
                    continue;
                }
                String slug = q.path("titleSlug").asText();
                problems.add(Map.of(
                        "title", q.path("title").asText(),
                        "difficulty", q.path("difficulty").asText(),
                        "url", "https://leetcode.com/problems/" + slug + "/"
                ));
            }
            return ToolResult.ok(Map.of("problems", problems));
        } catch (Exception e) {
            return ToolResult.error("LeetCode 응답 파싱 실패: " + e.getMessage());
        }
    }
}
