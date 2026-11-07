package com.swen3.indexing;

public class IndexException extends RuntimeException {
    public IndexException(Throwable cause) { super("Document indexing failed", cause); }
}
