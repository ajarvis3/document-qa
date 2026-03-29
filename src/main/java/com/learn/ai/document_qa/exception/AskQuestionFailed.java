package com.learn.ai.document_qa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        code = HttpStatus.BAD_GATEWAY,
        reason = "Failed to get answer from Gemini"
)
public class AskQuestionFailed extends RuntimeException {

    public AskQuestionFailed(String message, Throwable cause) {
        super(message, cause);
    }

    public AskQuestionFailed(String message) {
        super(message);
    }
}

