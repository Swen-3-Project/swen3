package com.swen3.ocr;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public Declarables ocrTopology() {
        DirectExchange exchange = new DirectExchange("paperless.documents", true, false);
        DirectExchange deadExchange = new DirectExchange("paperless.ocr.dead", true, false);
        Queue jobs = QueueBuilder.durable("paperless.ocr.jobs")
                .deadLetterExchange("paperless.ocr.dead").deadLetterRoutingKey("ocr.requested").build();
        Queue deadJobs = QueueBuilder.durable("paperless.ocr.dead-letter").build();
        Queue results = QueueBuilder.durable("paperless.ocr.results")
                .deadLetterExchange("paperless.ocr.dead").deadLetterRoutingKey("ocr.completed").build();
        Queue deadResults = QueueBuilder.durable("paperless.ocr.results.dead-letter").build();
        return new Declarables(exchange, deadExchange, jobs, deadJobs, results, deadResults,
                BindingBuilder.bind(jobs).to(exchange).with("ocr.requested"),
                BindingBuilder.bind(deadJobs).to(deadExchange).with("ocr.requested"),
                BindingBuilder.bind(results).to(exchange).with("ocr.completed"),
                BindingBuilder.bind(deadResults).to(deadExchange).with("ocr.completed"));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        var template = new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }
}
