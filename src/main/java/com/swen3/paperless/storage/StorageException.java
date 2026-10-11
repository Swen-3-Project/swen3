package com.swen3.paperless.storage;

public class StorageException extends RuntimeException {
    public StorageException(Throwable cause) { super("Document storage is unavailable", cause); }
}
