package com.swen3.indexing;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public Declarables indexTopology() {
        DirectExchange exchange = new DirectExchange("paperless.documents", true, false);
        DirectExchange deadExchange = new DirectExchange("paperless.index.dead", true, false);
        Queue jobs = QueueBuilder.durable("paperless.index.jobs")
                .deadLetterExchange("paperless.index.dead").deadLetterRoutingKey("index.requested").build();
        Queue deadJobs = QueueBuilder.durable("paperless.index.dead-letter").build();
        Queue results = QueueBuilder.durable("paperless.index.results")
                .deadLetterExchange("paperless.index.dead").deadLetterRoutingKey("index.completed").build();
        Queue deadResults = QueueBuilder.durable("paperless.index.results.dead-letter").build();
        return new Declarables(exchange, deadExchange, jobs, deadJobs, results, deadResults,
                BindingBuilder.bind(jobs).to(exchange).with("index.requested"),
                BindingBuilder.bind(deadJobs).to(deadExchange).with("index.requested"),
                BindingBuilder.bind(results).to(exchange).with("index.completed"),
                BindingBuilder.bind(deadResults).to(deadExchange).with("index.completed"));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        var template = new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }
}
