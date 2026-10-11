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
    public OcrJobListener(ObjectMapper mapper) { this.mapper = mapper; }

    @RabbitListener(queues = "paperless.ocr.jobs")
    public void receive(Message message) {
        OcrJob job;
        try {
            if (message.getBody().length > 4096) throw new IllegalArgumentException();
            job = mapper.readValue(message.getBody(), OcrJob.class);
            if (job == null || job.schemaVersion() != 1 || job.jobId() == null || job.documentId() == null
                    || !((job.documentId() + ".pdf").equals(job.storageKey()))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            log.warn("ocr_job_rejected reason=invalid_contract");
            // Do not include the parser exception: it may contain untrusted message content.
            throw new AmqpRejectAndDontRequeueException("Invalid OCR job contract");
        }
        try (var documentScope = MDC.putCloseable("documentId", job.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", job.jobId().toString())) {
            log.info("ocr_job_received storageKey={}", job.storageKey());
            // Stub only. Returning successfully lets AUTO acknowledgement confirm receipt.
        }
    }
}
