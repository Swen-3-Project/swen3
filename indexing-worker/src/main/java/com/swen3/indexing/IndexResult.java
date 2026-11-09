package com.swen3.indexing;

import java.util.UUID;

public record IndexResult(int schemaVersion, UUID jobId, UUID documentId, boolean success) { }
