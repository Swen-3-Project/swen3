package com.swen3.paperless.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 127) String mimeType,
        @NotNull @PositiveOrZero Long fileSize,
        @Size(max = 255) String title,
        String description) {
}
