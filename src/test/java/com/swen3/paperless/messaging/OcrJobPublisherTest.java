package com.swen3.paperless.messaging;

import java.util.UUID;
import com.swen3.paperless.jobs.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrJobPublisherTest {
    @Test
    void publishesPersistentJsonWithoutJavaTypeHeaders() {
        var template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var job = new PendingJob(JobKind.OCR_REQUESTED, UUID.randomUUID(), UUID.randomUUID() + ".pdf");
        var mapper = JsonMapper.builder().build();
        var publisher = new OcrJobPublisher(template, mapper);
        publisher.handle(job);
        assertEquals(JobKind.OCR_REQUESTED, publisher.kind());
        var message = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(RabbitConfiguration.EXCHANGE), eq(RabbitConfiguration.ROUTING_KEY), message.capture(), any());
        OcrJob payload = mapper.readValue(message.getValue().getBody(), OcrJob.class);
        assertEquals(1, payload.schemaVersion());
        assertEquals(job.getId(), payload.jobId());
        assertEquals(job.getDocumentId(), payload.documentId());
        assertEquals(job.getStorageKey(), payload.storageKey());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getValue().getMessageProperties().getDeliveryMode());
        assertFalse(message.getValue().getMessageProperties().getHeaders().containsKey("__TypeId__"));
    }

    @Test
    void nackAndUnroutablePublishAreFailures() {
        for (boolean ack : new boolean[] { false, true }) {
            var template = mock(RabbitTemplate.class);
            doAnswer(call -> {
                CorrelationData data = call.getArgument(3);
                if (ack) data.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "ex", "key"));
                data.getFuture().complete(new CorrelationData.Confirm(ack, null));
                return null;
            }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
            assertThrows(MessagingException.class, () -> new OcrJobPublisher(template, JsonMapper.builder().build())
                    .handle(new PendingJob(JobKind.OCR_REQUESTED, UUID.randomUUID(), "file.pdf")));
        }
    }

    @Test
    void declaresDurableTopologyAndMandatoryPublisher() {
        var config = new RabbitConfiguration();
        assertEquals(6, config.ocrTopology().getDeclarables().size());
        assertNotNull(config.rabbitTemplate(mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)));
    }
}
