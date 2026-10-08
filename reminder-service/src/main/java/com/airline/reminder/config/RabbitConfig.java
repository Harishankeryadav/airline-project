package com.airline.reminder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EVENT CONTRACT with booking-service (docs/ARCHITECTURE.md). Exchange/queues/bindings are declared here exactly
 * as booking-service declares them (same names, durable, no extra arguments) - RabbitMQ rejects a re-declaration
 * that differs, so do not add queue arguments on only one side.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "airline.events";

    public static final String CONFIRMED_QUEUE = "reminder.booking-confirmed";
    public static final String CONFIRMED_KEY = "booking.confirmed";

    public static final String CANCELLED_QUEUE = "reminder.booking-cancelled";
    public static final String CANCELLED_KEY = "booking.cancelled";

    /** Messages that still fail after all retries are parked here for an admin to inspect (RabbitMQ UI :15672). */
    public static final String DEAD_LETTER_EXCHANGE = "airline.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "reminder.dead-letter";
    public static final String DEAD_LETTER_KEY = "failed";

    @Bean
    public TopicExchange airlineEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(CONFIRMED_QUEUE).build();
    }

    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue, TopicExchange airlineEventsExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue).to(airlineEventsExchange).with(CONFIRMED_KEY);
    }

    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(CANCELLED_QUEUE).build();
    }

    @Bean
    public Binding bookingCancelledBinding(Queue bookingCancelledQueue, TopicExchange airlineEventsExchange) {
        return BindingBuilder.bind(bookingCancelledQueue).to(airlineEventsExchange).with(CANCELLED_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_KEY);
    }

    /**
     * JSON, and the target type comes from OUR listener method parameter (INFERRED), not from the __TypeId__ header,
     * which names a class that only exists inside booking-service.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public MessageRecoverer deadLetterRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, DEAD_LETTER_EXCHANGE, DEAD_LETTER_KEY);
    }
}
