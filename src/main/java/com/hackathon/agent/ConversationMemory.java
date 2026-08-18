package com.hackathon.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 단기 기억: 프로세스가 살아있는 동안의 대화 이력을 순서대로 보관한다. */
public class ConversationMemory {

    private final List<Message> history = new ArrayList<>();

    public void add(String role, String content) {
        history.add(new Message(role, content));
    }

    public List<Message> all() {
        return Collections.unmodifiableList(history);
    }
}
