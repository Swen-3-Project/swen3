package com.swen3.paperless.common;

public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException() { super("The PDF exceeds the upload limit"); }
}
