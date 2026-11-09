package com.swen3.indexing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IndexJobListener {
    private static final Logger log = LoggerFactory.getLogger(IndexJobListener.class);
    private final ObjectMapper mapper;
    private final IndexService indexing;
    private final ResultPublisher publisher;

    public IndexJobListener(ObjectMapper mapper, IndexService indexing, ResultPublisher publisher) {
        this.mapper = mapper;
        this.indexing = indexing;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "paperless.index.jobs")
    public void receive(Message message) {
        IndexJob job = readJob(message);
        try (var documentScope = MDC.putCloseable("documentId", job.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", job.jobId().toString())) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    indexing.index(job);
                } catch (IndexException exception) {
                    log.warn("index_attempt_failed attempt={} errorType={}", attempt, exception.getClass().getSimpleName());
                    continue;
                }
                publisher.publish(new IndexResult(1, job.jobId(), job.documentId(), true));
                log.info("index_job_completed");
                return;
            }
            publisher.publish(new IndexResult(1, job.jobId(), job.documentId(), false));
            throw new AmqpRejectAndDontRequeueException("Indexing failed after three attempts");
        }
    }

    private IndexJob readJob(Message message) {
        try {
            IndexJob job = mapper.readValue(message.getBody(), IndexJob.class);
            if (job == null || job.schemaVersion() != 1 || job.jobId() == null || job.documentId() == null
                    || job.filename() == null || job.filename().isBlank() || job.filename().length() > 255 || job.content() == null) {
                throw new IllegalArgumentException();
            }
            return job;
        } catch (Exception exception) {
            log.warn("index_job_rejected reason=invalid_contract");
            throw new AmqpRejectAndDontRequeueException("Invalid index job contract");
        }
    }
}
