package com.swen3.paperless.jobs;

public interface JobHandler {
    JobKind kind();
    void handle(PendingJob job);
}
