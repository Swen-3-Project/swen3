package com.swen3.ocr;

import java.util.UUID;

/** JSON contract shared with the REST publisher, independent of Java package names. */
public record OcrJob(int schemaVersion, UUID jobId, UUID documentId, String storageKey) { }
