package com.swen3.paperless.common;

public class UploadValidationException extends RuntimeException {
    private final String field;
    public UploadValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
    public String getField() { return field; }
}
