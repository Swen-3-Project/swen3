package com.swen3.paperless.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.swen3.paperless.document.DocumentStatus;

public record DocumentResponse(
        UUID id,
        String filename,
        String mimeType,
        long fileSize,
        DocumentStatus status,
        Instant createdAt,
        String title,
        String description) {
}
