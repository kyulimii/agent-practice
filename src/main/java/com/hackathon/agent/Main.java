package com.hackathon.agent;

import com.hackathon.agent.gemini.GeminiClient;
import com.hackathon.agent.memory.ConversationMemory;
import com.hackathon.agent.memory.Message;
import com.hackathon.agent.memory.Part;
import com.hackathon.agent.tools.LeetCodeSearchTool;
import com.hackathon.agent.tools.ProgrammersRecommendTool;
import com.hackathon.agent.tools.ToolRegistry;
import com.hackathon.agent.tools.ToolResult;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.util.Scanner;

/** 2단계: 도구(함수 호출)를 스스로 골라 부르는 CLI 에이전트. */
public class Main {

    private static final int MAX_TOOL_ROUNDS = 5;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = firstNonBlank(System.getenv("GEMINI_API_KEY"), dotenv.get("GEMINI_API_KEY"));
        String model = firstNonBlank(System.getenv("GEMINI_MODEL"), dotenv.get("GEMINI_MODEL"), "gemini-3.6-flash");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("GEMINI_API_KEY가 설정되어 있지 않습니다. .env 파일을 만들거나 환경변수를 설정하세요.");
            System.err.println("예: cp .env.example .env  후 GEMINI_API_KEY 값을 채워주세요.");
            return;
        }

        GeminiClient client = new GeminiClient(apiKey, model);
        ConversationMemory memory = new ConversationMemory();
        ToolRegistry tools = new ToolRegistry(List.of(
                new LeetCodeSearchTool(),
                new ProgrammersRecommendTool()
        ));

        System.out.println("에이전트와 대화를 시작합니다. 종료하려면 'exit' 또는 'quit'을 입력하세요.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("나> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    System.out.println("대화를 종료합니다.");
                    break;
                }

                memory.add(Message.userText(input));
                try {
                    handleTurn(client, memory, tools);
                } catch (RuntimeException e) {
                    System.out.println("[오류] 예상치 못한 문제가 발생했어요. 다시 시도해주세요.");
                }
            }
        }
    }

    private static void handleTurn(GeminiClient client, ConversationMemory memory, ToolRegistry tools) {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Message response;
            try {
                response = client.send(memory.all(), tools.all());
            } catch (GeminiClient.GeminiException e) {
                System.out.println("[오류] " + e.userMessage());
                return;
            }
            memory.add(response);

            List<Part.FunctionCall> calls = response.functionCalls();
            if (calls.isEmpty()) {
                System.out.println("에이전트> " + response.textOrEmpty());
                return;
            }

            for (Part.FunctionCall call : calls) {
                System.out.println("[도구 호출] " + call.name() + "(" + call.args() + ")");
                ToolResult result = tools.execute(call.name(), call.args());
                if (!result.ok()) {
                    System.out.println("[도구 실패] " + result.error());
                }
                memory.add(Message.functionResponse(call.name(), call.id(), result.asResponsePayload()));
            }
        }

        System.out.println("[오류] 도구 호출이 " + MAX_TOOL_ROUNDS + "회를 넘어서 중단했습니다.");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
