package com.swen3.paperless.messaging;

public class MessagingException extends RuntimeException {
    public MessagingException(Throwable cause) {
        super("The OCR job could not be delivered", cause);
    }
}
