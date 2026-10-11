package com.swen3.paperless.messaging;

import java.util.UUID;

/** Versioned wire contract; messages contain references, never PDF bytes. */
public record OcrJob(int schemaVersion, UUID jobId, UUID documentId, String storageKey) {
}
