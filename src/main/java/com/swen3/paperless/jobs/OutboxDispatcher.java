package com.swen3.paperless.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final PendingJobRepository repository;
    private final Map<JobKind, JobHandler> handlers;

    public OutboxDispatcher(PendingJobRepository repository, List<JobHandler> handlers) {
        this.repository = repository;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::kind, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${jobs.poll-ms:3000}")
    @Transactional
    public void dispatch() {
        Instant now = Instant.now();
        for (PendingJob job : repository.lockDue(now, PageRequest.of(0, 10))) {
            try (var documentScope = MDC.putCloseable("documentId", job.getDocumentId().toString());
                 var jobScope = MDC.putCloseable("jobId", job.getId().toString())) {
                try {
                    JobHandler handler = handlers.get(job.getKind());
                    if (handler == null) throw new IllegalStateException("Missing job handler");
                    handler.handle(job);
                    job.sent();
                    log.info("job_completed kind={}", job.getKind());
                } catch (RuntimeException exception) {
                    job.retry(now);
                    log.warn("job_retry kind={} attempt={} errorType={}",
                            job.getKind(), job.getAttempts(), exception.getClass().getSimpleName());
                }
                repository.save(job);
            }
        }
    }
}
