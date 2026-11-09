package com.swen3.ocr;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrJobListenerTest {
    private final OcrService ocr = mock(OcrService.class);
    private final ResultPublisher publisher = mock(ResultPublisher.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OcrJobListener listener = new OcrJobListener(mapper, ocr, publisher);
    private final UUID id = UUID.randomUUID();
    private final OcrJob job = new OcrJob(1, UUID.randomUUID(), id, id + ".pdf");

    @Test void deliversTextWithTheOriginalIdentity() {
        when(ocr.extract(job.storageKey())).thenReturn("Hello World");
        listener.receive(message());
        verify(publisher).publish(new OcrResult(1, job.jobId(), id, true, "Hello World"));
        assertNull(org.slf4j.MDC.get("documentId"));
    }

    @Test void retriesOcrFailureThreeTimesAndReportsFailure() {
        when(ocr.extract(job.storageKey())).thenThrow(new OcrException("invalid PDF"));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message()));
        verify(ocr, times(3)).extract(job.storageKey());
        verify(publisher).publish(new OcrResult(1, job.jobId(), id, false, null));
    }

    @Test void leavesResultDeliveryFailuresAvailableForRedelivery() {
        when(ocr.extract(job.storageKey())).thenReturn("Hello");
        doThrow(new AmqpException("broker unavailable")).when(publisher).publish(any());
        assertThrows(AmqpException.class, () -> listener.receive(message()));
        verify(ocr).extract(job.storageKey());
    }

    @Test void rejectsInvalidMessagesBeforeOcr() {
        for (String json : List.of("not JSON", "null", "{}", "{\"schemaVersion\":2}",
                "{\"schemaVersion\":1,\"jobId\":\"not-uuid\"}",
                "{\"schemaVersion\":1,\"jobId\":\"" + job.jobId() + "\",\"documentId\":\"" + id + "\",\"storageKey\":\"../secret\"}",
                "x".repeat(4097))) {
            var error = assertThrows(AmqpRejectAndDontRequeueException.class,
                    () -> listener.receive(new Message(json.getBytes(StandardCharsets.UTF_8))));
            assertNull(error.getCause());
        }
        verifyNoInteractions(ocr, publisher);
    }

    @Test void topologyIsDurableAndSupportsResults() {
        var configuration = new RabbitConfiguration();
        var queues = configuration.ocrTopology().getDeclarablesByType(org.springframework.amqp.core.Queue.class);
        assertEquals(4, queues.size());
        assertTrue(queues.stream().allMatch(org.springframework.amqp.core.Queue::isDurable));
        assertNotNull(configuration.rabbitTemplate(mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)));
    }

    private Message message() { return new Message(mapper.writeValueAsBytes(job)); }
}
