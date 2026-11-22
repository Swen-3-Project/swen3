package com.swen3.genai;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SummaryJobListener {
    private static final Logger log = LoggerFactory.getLogger(SummaryJobListener.class);
    private static final int MAX_ATTEMPTS = 3;
    private final ObjectMapper mapper;
    private final GeminiClient gemini;
    private final ResultPublisher publisher;

    public SummaryJobListener(ObjectMapper mapper, GeminiClient gemini, ResultPublisher publisher) {
        this.mapper = mapper;
        this.gemini = gemini;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "paperless.genai.jobs")
    public void receive(Message message) {
        SummaryJob job = readJob(message);
        try (var documentScope = MDC.putCloseable("documentId", job.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", job.jobId().toString())) {
            if (!gemini.configured()) {
                log.info("summary_job_skipped reason=missing_api_key");
                return;
            }
            log.info("summary_job_received");
            String summary = summarize(job.text());
            publisher.publish(new SummaryResult(1, job.jobId(), job.documentId(), summary));
            log.info("summary_job_completed");
        }
    }

    private String summarize(String text) {
        for (int attempt = 1; ; attempt++) {
            try {
                return gemini.summarize(text);
            } catch (GeminiException exception) {
                log.warn("summary_attempt_failed attempt={} reason={}", attempt, exception.getMessage());
                if (!exception.isRetryable() || attempt == MAX_ATTEMPTS) {
                    throw new AmqpRejectAndDontRequeueException("Summary generation failed");
                }
                try {
                    Thread.sleep(Duration.ofSeconds(attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AmqpException("Summary processing interrupted", interrupted);
                }
            }
        }
    }

    private SummaryJob readJob(Message message) {
        try {
            SummaryJob job = mapper.readValue(message.getBody(), SummaryJob.class);
            if (job != null && job.schemaVersion() == 1 && job.jobId() != null && job.documentId() != null
                    && job.text() != null && !job.text().isBlank()) {
                return job;
            }
        } catch (RuntimeException ignored) {
            // Reject malformed JSON without logging document text.
        }
        log.warn("summary_job_rejected reason=invalid_contract");
        throw new AmqpRejectAndDontRequeueException("Invalid summary job");
    }
}
