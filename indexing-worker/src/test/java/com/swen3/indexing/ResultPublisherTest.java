package com.swen3.indexing;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ResultPublisherTest {
    @Test void confirmsPersistentJsonAndRejectsBrokerNacks() {
        var template = mock(RabbitTemplate.class);
        var mapper = JsonMapper.builder().build();
        doAnswer(call -> {
            CorrelationData confirmation = call.getArgument(3);
            confirmation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var result = new IndexResult(1, UUID.randomUUID(), UUID.randomUUID(), true);
        var publisher = new ResultPublisher(template, mapper);
        publisher.publish(result);
        var sent = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq("paperless.documents"), eq("index.completed"), sent.capture(), any());
        assertEquals(result, mapper.readValue(sent.getValue().getBody(), IndexResult.class));
        assertEquals(MessageDeliveryMode.PERSISTENT, sent.getValue().getMessageProperties().getDeliveryMode());
        doAnswer(call -> {
            CorrelationData confirmation = call.getArgument(3);
            confirmation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThrows(AmqpException.class, () -> publisher.publish(result));
    }
}
