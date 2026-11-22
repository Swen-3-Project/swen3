package com.swen3.genai;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResultPublisher {
    private final RabbitTemplate template;
    private final ObjectMapper mapper;

    public ResultPublisher(RabbitTemplate template, ObjectMapper mapper) {
        this.template = template;
        this.mapper = mapper;
    }

    public void publish(SummaryResult result) {
        var confirmation = new CorrelationData(result.jobId().toString());
        try {
            var message = MessageBuilder.withBody(mapper.writeValueAsBytes(result))
                    .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(result.jobId().toString()).setCorrelationId(result.documentId().toString()).build();
            template.send("paperless.documents", "summary.completed", message, confirmation);
            if (!confirmation.getFuture().get(2, TimeUnit.SECONDS).ack() || confirmation.getReturned() != null) {
                throw new AmqpException("Summary result was not confirmed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Summary result delivery interrupted", exception);
        } catch (Exception exception) {
            throw new AmqpException("Summary result could not be delivered", exception);
        }
    }
}
