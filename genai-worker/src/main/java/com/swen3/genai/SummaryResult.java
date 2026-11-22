package com.swen3.genai;

import java.util.UUID;

public record SummaryResult(int schemaVersion, UUID jobId, UUID documentId, String summary) { }
