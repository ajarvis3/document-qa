package com.learn.ai.document_qa.dto;

public record AnswerResponse(String answer, String source, boolean success) {

    public static AnswerResponse fromGemini(String answer) {
        return new AnswerResponse(answer, "gemini", true);
    }

    public static AnswerResponse fallback(String answer) {
        return new AnswerResponse(answer, "fallback", false);
    }
}

