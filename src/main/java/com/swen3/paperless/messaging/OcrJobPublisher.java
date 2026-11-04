package com.swen3.paperless.messaging;

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
public class OcrJobPublisher {
    private static final Logger log = LoggerFactory.getLogger(OcrJobPublisher.class);
    private final RabbitTemplate template;
    private final ObjectMapper mapper;

    public OcrJobPublisher(RabbitTemplate template, ObjectMapper mapper) {
        this.template = template;
        this.mapper = mapper;
    }

    public void publish(OcrJob job) {
        CorrelationData correlation = new CorrelationData(job.jobId().toString());
        try {
            var message = MessageBuilder.withBody(mapper.writeValueAsBytes(job))
                    .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(job.jobId().toString()).setCorrelationId(job.documentId().toString()).build();
            template.send(RabbitConfiguration.EXCHANGE, RabbitConfiguration.ROUTING_KEY, message, correlation);
            if (!correlation.getFuture().get(2, TimeUnit.SECONDS).ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Broker did not route and confirm the message");
            }
            log.info("ocr_job_published");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MessagingException(exception);
        } catch (Exception exception) {
            throw new MessagingException(exception);
        }
    }
}
