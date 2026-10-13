package com.swen3.ocr;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public Declarables ocrTopology() {
        DirectExchange exchange = new DirectExchange("paperless.documents", true, false);
        DirectExchange deadExchange = new DirectExchange("paperless.ocr.dead", true, false);
        Queue queue = QueueBuilder.durable("paperless.ocr.jobs")
                .deadLetterExchange("paperless.ocr.dead").deadLetterRoutingKey("ocr.requested").build();
        Queue deadQueue = QueueBuilder.durable("paperless.ocr.dead-letter").build();
        return new Declarables(exchange, queue, deadExchange, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with("ocr.requested"),
                BindingBuilder.bind(deadQueue).to(deadExchange).with("ocr.requested"));
    }
}
