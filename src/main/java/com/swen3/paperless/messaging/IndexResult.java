package com.swen3.paperless.messaging;

import java.util.UUID;

public record IndexResult(int schemaVersion, UUID jobId, UUID documentId, boolean success) { }
