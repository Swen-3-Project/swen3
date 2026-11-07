package com.swen3.paperless.messaging;

import java.util.UUID;

public record IndexJob(int schemaVersion, UUID jobId, UUID documentId, String filename, String content) { }
