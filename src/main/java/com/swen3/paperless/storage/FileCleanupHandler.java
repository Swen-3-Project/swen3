package com.swen3.paperless.storage;

import com.swen3.paperless.jobs.*;
import org.springframework.stereotype.Component;

@Component
public class FileCleanupHandler implements JobHandler {
    private final FileStorage storage;
    public FileCleanupHandler(FileStorage storage) { this.storage = storage; }
    @Override public JobKind kind() { return JobKind.DELETE_FILE; }
    @Override public void handle(PendingJob job) { storage.delete(job.getStorageKey()); }
}
