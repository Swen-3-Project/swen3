package com.swen3.paperless.messaging;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JobPublisher {
    private static final Logger log = LoggerFactory.getLogger(JobPublisher.class);
    private final RabbitTemplate template;
    private final ObjectMapper mapper;

    public JobPublisher(RabbitTemplate template, ObjectMapper mapper) {
        this.template = template;
        this.mapper = mapper;
    }

    public void publish(OcrJob job) {
        send("ocr.requested", job, job.jobId(), job.documentId());
    }

    public void publish(IndexJob job) {
        send("index.requested", job, job.jobId(), job.documentId());
    }

    private void send(String routingKey, Object payload, UUID jobId, UUID documentId) {
        var confirmation = new CorrelationData(jobId.toString());
        try {
            var message = MessageBuilder.withBody(mapper.writeValueAsBytes(payload))
                    .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(jobId.toString()).setCorrelationId(documentId.toString()).build();
            template.send(RabbitConfiguration.EXCHANGE, routingKey, message, confirmation);
            if (!confirmation.getFuture().get(2, TimeUnit.SECONDS).ack() || confirmation.getReturned() != null) {
                throw new IllegalStateException("Broker did not route and confirm the message");
            }
            log.info("job_published routingKey={} jobId={}", routingKey, jobId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MessagingException(exception);
        } catch (Exception exception) {
            throw new MessagingException(exception);
        }
    }
}
