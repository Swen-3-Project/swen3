package com.swen3.paperless.search;

public class SearchException extends RuntimeException {
    public SearchException(Throwable cause) { super("Document search is unavailable", cause); }
}
