package com.swen3.genai;

class GeminiException extends RuntimeException {
    private final boolean retryable;

    GeminiException(String reason, boolean retryable) {
        super(reason);
        this.retryable = retryable;
    }

    boolean isRetryable() {
        return retryable;
    }
}
