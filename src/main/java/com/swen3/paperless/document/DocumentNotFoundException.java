package com.swen3.paperless.document;

import java.util.UUID;
import com.swen3.paperless.common.ResourceNotFoundException;

public class DocumentNotFoundException extends ResourceNotFoundException {
    public DocumentNotFoundException(UUID id) { super("Document " + id + " was not found"); }
}
