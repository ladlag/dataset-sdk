package com.knowledge.sdk.exception;

public class KnowledgeException extends RuntimeException {

    private int statusCode;

    public KnowledgeException(String message) {
        super(message);
    }

    public KnowledgeException(String message, Throwable cause) {
        super(message, cause);
    }

    public KnowledgeException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public KnowledgeException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
