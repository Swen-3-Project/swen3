package com.swen3.ocr;

import java.util.UUID;

public record OcrResult(int schemaVersion, UUID jobId, UUID documentId, boolean success, String text) { }
