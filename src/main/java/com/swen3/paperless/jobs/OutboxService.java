package com.swen3.paperless.jobs;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final PendingJobRepository repository;
    public OutboxService(PendingJobRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(JobKind kind, UUID documentId, String storageKey) {
        repository.save(new PendingJob(kind, documentId, storageKey));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPendingOcr(UUID documentId) { repository.cancelPendingOcr(documentId); }
}
