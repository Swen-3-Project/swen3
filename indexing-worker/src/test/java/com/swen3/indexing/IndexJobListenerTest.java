package com.swen3.indexing;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndexJobListenerTest {
    IndexService indexing = mock(IndexService.class);
    ResultPublisher publisher = mock(ResultPublisher.class);
    JsonMapper mapper = JsonMapper.builder().build();
    IndexJobListener listener = new IndexJobListener(mapper, indexing, publisher);
    UUID id = UUID.randomUUID(), jobId = UUID.randomUUID();
    IndexJob job = new IndexJob(1, jobId, id, "a.pdf", "Hello");

    @Test void reportsSuccessAfterIndexing() {
        listener.receive(message(job));
        var order = inOrder(indexing, publisher);
        order.verify(indexing).index(job);
        order.verify(publisher).publish(new IndexResult(1, jobId, id, true));
        assertNull(org.slf4j.MDC.get("documentId"));
    }

    @Test void reportsFailureAfterThreeAttempts() {
        doThrow(new IndexException(new IllegalStateException())).when(indexing).index(job);
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(job)));
        verify(indexing, times(3)).index(job);
        verify(publisher).publish(new IndexResult(1, jobId, id, false));
    }

    @Test void leavesDeliveryFailuresForRedelivery() {
        doThrow(new AmqpException("broker unavailable")).when(publisher).publish(any());
        assertThrows(AmqpException.class, () -> listener.receive(message(job)));
    }

    @Test void rejectsInvalidJobs() {
        for (IndexJob invalid : List.of(new IndexJob(2, jobId, id, "a.pdf", "Hello"),
                new IndexJob(1, null, id, "a.pdf", ""), new IndexJob(1, jobId, null, "a.pdf", ""),
                new IndexJob(1, jobId, id, "", ""), new IndexJob(1, jobId, id, null, ""),
                new IndexJob(1, jobId, id, "a.pdf", null), new IndexJob(1, jobId, id, "x".repeat(256), ""))) {
            assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(invalid)));
        }
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message("invalid".getBytes())));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message("null".getBytes())));
        verifyNoInteractions(indexing, publisher);
    }

    @Test void topologyAndPublisherSupportDurableDelivery() {
        var config = new RabbitConfiguration();
        var queues = config.indexTopology().getDeclarablesByType(org.springframework.amqp.core.Queue.class);
        assertEquals(4, queues.size());
        assertTrue(queues.stream().allMatch(org.springframework.amqp.core.Queue::isDurable));
        assertNotNull(config.rabbitTemplate(mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)));
    }

    private Message message(IndexJob payload) { return new Message(mapper.writeValueAsBytes(payload)); }
}
