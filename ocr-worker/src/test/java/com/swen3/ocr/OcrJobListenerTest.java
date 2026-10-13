package com.swen3.ocr;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class OcrJobListenerTest {
    @Test void logsIdentityAndAcceptsRedeliveryWithoutSideEffects() {
        UUID id = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var mapper = JsonMapper.builder().build();
        var message = new Message(mapper.writeValueAsBytes(new OcrJob(1, jobId, id, id + ".pdf")));
        var listener = new OcrJobListener(mapper);
        var logger = (Logger) LoggerFactory.getLogger(OcrJobListener.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            listener.receive(message);
            listener.receive(message);
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("ocr_job_received"));
            assertEquals(id.toString(), appender.list.getFirst().getMDCPropertyMap().get("documentId"));
            assertEquals(jobId.toString(), appender.list.getFirst().getMDCPropertyMap().get("jobId"));
            assertNull(MDC.get("documentId"));
        } finally { logger.detachAppender(appender); }
    }

    @Test void rejectsMalformedUnsupportedAndUnsafeJobsWithoutRequeue() {
        var listener = new OcrJobListener(JsonMapper.builder().build());
        for (String json : List.of("not JSON", "null", "{}", "{\"schemaVersion\":2}",
                "{\"schemaVersion\":1,\"jobId\":\"not-uuid\"}",
                "{\"schemaVersion\":1,\"jobId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"documentId\":\"00000000-0000-0000-0000-000000000002\",\"storageKey\":\"../secret\"}",
                "x".repeat(4097))) {
            var exception = assertThrows(AmqpRejectAndDontRequeueException.class,
                    () -> listener.receive(new Message(json.getBytes(StandardCharsets.UTF_8))));
            assertEquals("Invalid OCR job contract", exception.getMessage());
            assertNull(exception.getCause());
        }
    }

    @Test void topologyIsDurableAndRejectedMessagesHaveADeadLetterRoute() {
        var topology = new RabbitConfiguration().ocrTopology();
        var queues = topology.getDeclarablesByType(org.springframework.amqp.core.Queue.class);
        assertEquals(2, queues.size());
        assertTrue(queues.stream().allMatch(org.springframework.amqp.core.Queue::isDurable));
        assertEquals("paperless.ocr.dead", queues.getFirst().getArguments().get("x-dead-letter-exchange"));
    }
}
