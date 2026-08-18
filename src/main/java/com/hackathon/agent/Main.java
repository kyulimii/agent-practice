package com.hackathon.agent;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Scanner;

/** 1단계: CLI에서 대화가 이어지는 최소 에이전트 (단기 기억만). */
public class Main {

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

                memory.add("user", input);

                try {
                    String reply = client.send(memory.all());
                    memory.add("model", reply);
                    System.out.println("에이전트> " + reply);
                } catch (GeminiClient.GeminiException e) {
                    System.out.println("[오류] " + e.getMessage());
                }
            }
        }
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
