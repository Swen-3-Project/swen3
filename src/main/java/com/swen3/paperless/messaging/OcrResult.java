package com.swen3.paperless.messaging;

import java.util.UUID;

public record OcrResult(int schemaVersion, UUID jobId, UUID documentId, boolean success, String text) { }
