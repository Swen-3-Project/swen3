package com.swen3.paperless.messaging;

import java.util.UUID;
import com.swen3.paperless.document.DocumentService;
import com.swen3.paperless.document.DocumentStatus;
import com.swen3.paperless.search.SearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProcessingResultListener {
    private static final Logger log = LoggerFactory.getLogger(ProcessingResultListener.class);
    private final ObjectMapper mapper;
    private final DocumentService documents;
    private final JobPublisher publisher;
    private final SearchClient search;

    public ProcessingResultListener(ObjectMapper mapper, DocumentService documents, JobPublisher publisher, SearchClient search) {
        this.mapper = mapper;
        this.documents = documents;
        this.publisher = publisher;
        this.search = search;
    }

    @RabbitListener(queues = {"paperless.ocr.results", "paperless.index.results"}, concurrency = "1")
    public void receive(Message message) {
        String route = message.getMessageProperties().getReceivedRoutingKey();
        if ("ocr.completed".equals(route)) {
            acceptOcr(read(message, OcrResult.class));
        } else if ("index.completed".equals(route)) {
            acceptIndex(read(message, IndexResult.class));
        } else {
            throw invalidResult();
        }
    }

    private void acceptOcr(OcrResult result) {
        if (result == null) throw invalidResult();
        validateIdentity(result.schemaVersion(), result.jobId(), result.documentId());
        if (result.success() && result.text() == null) throw invalidResult();
        try (var documentScope = MDC.putCloseable("documentId", result.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", result.jobId().toString())) {
            // Commit text first. A failed send can safely redeliver this result and index the same ID again.
            var document = documents.applyOcrResult(result.documentId(), result.success(), result.text());
            if (document == null) {
                log.info("ocr_result_ignored reason=document_deleted");
                return;
            }
            if (result.success() && document.getStatus() != DocumentStatus.INDEXED) {
                publisher.publish(new IndexJob(1, result.jobId(), document.getId(),
                        document.getFilename(), document.getOcrText()));
            }
            log.info("ocr_result_saved success={}", result.success());
        }
    }

    private void acceptIndex(IndexResult result) {
        if (result == null) throw invalidResult();
        validateIdentity(result.schemaVersion(), result.jobId(), result.documentId());
        try (var documentScope = MDC.putCloseable("documentId", result.documentId().toString());
             var jobScope = MDC.putCloseable("jobId", result.jobId().toString())) {
            if (!documents.applyIndexResult(result.documentId(), result.success()) && result.success()) {
                search.delete(result.documentId());
            }
            log.info("index_result_saved success={}", result.success());
        }
    }

    private void validateIdentity(int version, UUID jobId, UUID documentId) {
        if (version != 1 || jobId == null || documentId == null) throw invalidResult();
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            return mapper.readValue(message.getBody(), type);
        } catch (Exception exception) {
            throw invalidResult();
        }
    }

    private AmqpRejectAndDontRequeueException invalidResult() {
        log.warn("processing_result_rejected reason=invalid_contract");
        return new AmqpRejectAndDontRequeueException("Invalid processing result");
    }
}
