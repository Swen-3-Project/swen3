package com.swen3.paperless.document.dto;

import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(
        @Size(max = 255) String title,
        String description) {
}
