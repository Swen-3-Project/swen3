package com.swen3.paperless.jobs;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "pending_jobs")
public class PendingJob {
    @Id private UUID id;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private JobKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private JobState state;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;

    protected PendingJob() { }

    public PendingJob(JobKind kind, UUID documentId, String storageKey) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.storageKey = storageKey;
        this.kind = kind;
        this.state = JobState.PENDING;
        this.nextAttemptAt = Instant.now();
    }

    public void sent() { state = JobState.SENT; }
    public void retry(Instant now) {
        attempts++;
        nextAttemptAt = now.plusSeconds(Math.min(60, 3L << Math.min(attempts - 1, 5)));
    }
    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getStorageKey() { return storageKey; }
    public JobKind getKind() { return kind; }
    public JobState getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
