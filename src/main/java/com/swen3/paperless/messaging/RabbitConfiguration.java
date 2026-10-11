package com.swen3.paperless.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String EXCHANGE = "paperless.documents";
    public static final String QUEUE = "paperless.ocr.jobs";
    public static final String ROUTING_KEY = "ocr.requested";
    public static final String DEAD_EXCHANGE = "paperless.ocr.dead";
    public static final String DEAD_QUEUE = "paperless.ocr.dead-letter";

    @Bean
    public Declarables ocrTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadExchange = new DirectExchange(DEAD_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY).build();
        Queue deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(exchange, queue, deadExchange, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(ROUTING_KEY));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }
}
