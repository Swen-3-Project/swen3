package com.swen3.genai;

import java.util.UUID;

public record SummaryJob(int schemaVersion, UUID jobId, UUID documentId, String text) { }
