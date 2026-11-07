package com.swen3.paperless.messaging;

import java.util.ArrayList;
import java.util.List;
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

    @Bean
    public Declarables ocrTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var ocrDead = new DirectExchange("paperless.ocr.dead", true, false);
        var indexDead = new DirectExchange("paperless.index.dead", true, false);
        var topology = new ArrayList<Declarable>(List.of(exchange, ocrDead, indexDead));
        addQueue(topology, exchange, ocrDead, QUEUE, "paperless.ocr.dead-letter", ROUTING_KEY);
        addQueue(topology, exchange, ocrDead, "paperless.ocr.results", "paperless.ocr.results.dead-letter", "ocr.completed");
        addQueue(topology, exchange, indexDead, "paperless.index.jobs", "paperless.index.dead-letter", "index.requested");
        addQueue(topology, exchange, indexDead, "paperless.index.results", "paperless.index.results.dead-letter", "index.completed");
        return new Declarables(topology);
    }

    private void addQueue(List<Declarable> topology, DirectExchange exchange, DirectExchange deadExchange,
                           String name, String deadName, String routingKey) {
        var queue = QueueBuilder.durable(name).deadLetterExchange(deadExchange.getName()).deadLetterRoutingKey(routingKey).build();
        var deadQueue = QueueBuilder.durable(deadName).build();
        topology.addAll(List.of(queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(routingKey),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(routingKey)));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        var template = new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }
}
