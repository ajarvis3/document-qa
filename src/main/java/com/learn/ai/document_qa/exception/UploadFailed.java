package com.learn.ai.document_qa.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        code = HttpStatus.INTERNAL_SERVER_ERROR,
        reason = "Failed to upload document"
)
public class UploadFailed extends RuntimeException {

    public UploadFailed(String message, Throwable cause) {
        super(message, cause);
    }

    public UploadFailed(String message) {
        super(message);
    }
}

