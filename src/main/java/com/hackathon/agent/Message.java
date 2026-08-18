package com.hackathon.agent;

/** 대화 이력 한 줄. role은 "user" 또는 "model". */
public record Message(String role, String content) {
}
