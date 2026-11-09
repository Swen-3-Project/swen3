package com.swen3.indexing;

import java.util.UUID;

public record IndexJob(int schemaVersion, UUID jobId, UUID documentId, String filename, String content) { }
