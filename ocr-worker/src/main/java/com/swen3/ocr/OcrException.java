package com.swen3.ocr;

public class OcrException extends RuntimeException {
    public OcrException(String message) { super(message); }
    public OcrException(Throwable cause) { super("PDF text recognition failed", cause); }
}
