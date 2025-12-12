package com.ecm.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * RabbitMQ configuration to ensure objects are serialized as JSON when sending
 * events/notifications. Prevents SimpleMessageConverter from rejecting non
 * primitive payloads (e.g., DocumentCreatedMessage).
 */
@Configuration
public class RabbitConfig {

    @Value("${ecm.events.exchange:athena.events}")
    private String eventsExchange;

    @Value("${ecm.events.queue:athena.events.document}")
    private String eventsQueue;

    @Value("${ecm.events.routing-key.created:document.created}")
    private String createdRoutingKey;

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        // Use the global ObjectMapper (with JavaTimeModule, etc.) for message conversion
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Ensure the shared RabbitTemplate uses JSON conversion for all sends.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /**
     * Ensure the events exchange exists (durable topic exchange).
     */
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(eventsExchange, true, false);
    }

    /**
     * Declare exchange, default queue and binding on startup to avoid "NOT_FOUND exchange" errors.
     */
    @Bean
    public Declarables eventDeclarables() {
        Queue queue = new Queue(eventsQueue, true);
        TopicExchange exchange = new TopicExchange(eventsExchange, true, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(createdRoutingKey);
        return new Declarables(exchange, queue, binding);
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new org.springframework.amqp.rabbit.core.RabbitAdmin(connectionFactory);
    }
}
