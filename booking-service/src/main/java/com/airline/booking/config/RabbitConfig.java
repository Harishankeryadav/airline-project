package com.airline.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EVENT CONTRACT with reminder-service (see docs/ARCHITECTURE.md):
 * exchange airline.events (topic, durable) -> routing key booking.confirmed -> queue reminder.booking-confirmed,
 *                                          -> routing key booking.cancelled -> queue reminder.booking-cancelled.
 * The queue is declared here too, so events published before reminder-service exists simply wait in the queue.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "airline.events";
    public static final String ROUTING_KEY = "booking.confirmed";
    public static final String QUEUE = "reminder.booking-confirmed";

    public static final String CANCELLED_ROUTING_KEY = "booking.cancelled";
    public static final String CANCELLED_QUEUE = "reminder.booking-cancelled";

    @Bean
    public TopicExchange airlineEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding bookingConfirmedBinding(Queue bookingConfirmedQueue, TopicExchange airlineEventsExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue).to(airlineEventsExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder.durable(CANCELLED_QUEUE).build();
    }

    @Bean
    public Binding bookingCancelledBinding(Queue bookingCancelledQueue, TopicExchange airlineEventsExchange) {
        return BindingBuilder.bind(bookingCancelledQueue).to(airlineEventsExchange).with(CANCELLED_ROUTING_KEY);
    }

    /** JSON (not Java serialisation) so any service/language can consume the event. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
