package com.swen3.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OcrJobListener {
    private static final Logger log = LoggerFactory.getLogger(OcrJobListener.class);
    private final ObjectMapper mapper;
    private final OcrService ocr;
    private final ResultPublisher publisher;

    public OcrJobListener(ObjectMapper mapper, OcrService ocr, ResultPublisher publisher) {
        this.mapper = mapper;
        this.ocr = ocr;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "paperless.ocr.jobs")
    public void receive(Message message) {
        OcrJob job = readJob(message);
        try (var documentScope = MDC.putCloseable("documentId", job.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", job.jobId().toString())) {
            log.info("ocr_job_received storageKey={}", job.storageKey());
            for (int attempt = 1; attempt <= 3; attempt++) {
                String text;
                try {
                    text = ocr.extract(job.storageKey());
                } catch (OcrException exception) {
                    log.warn("ocr_attempt_failed attempt={} errorType={}", attempt, exception.getClass().getSimpleName());
                    continue;
                }
                publisher.publish(new OcrResult(1, job.jobId(), job.documentId(), true, text));
                log.info("ocr_job_completed");
                return;
            }
            publisher.publish(new OcrResult(1, job.jobId(), job.documentId(), false, null));
            throw new AmqpRejectAndDontRequeueException("PDF recognition failed after three attempts");
        }
    }

    private OcrJob readJob(Message message) {
        try {
            if (message.getBody().length > 4096) throw new IllegalArgumentException();
            OcrJob job = mapper.readValue(message.getBody(), OcrJob.class);
            if (job == null || job.schemaVersion() != 1 || job.jobId() == null || job.documentId() == null
                    || !(job.documentId() + ".pdf").equals(job.storageKey())) {
                throw new IllegalArgumentException();
            }
            return job;
        } catch (Exception exception) {
            log.warn("ocr_job_rejected reason=invalid_contract");
            throw new AmqpRejectAndDontRequeueException("Invalid OCR job contract");
        }
    }
}
