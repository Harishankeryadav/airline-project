package com.airline.reminder.event;

import com.airline.reminder.config.RabbitConfig;
import com.airline.reminder.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes booking-service events. The message is acknowledged only after the notifications were saved; if saving
 * throws, the listener retries (see spring.rabbitmq.listener.simple.retry) and then the message is parked in the
 * dead-letter queue. Duplicates are harmless (NotificationService is idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.CONFIRMED_QUEUE)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received {}", event.eventId());
        notificationService.handleBookingConfirmed(event);
    }

    @RabbitListener(queues = RabbitConfig.CANCELLED_QUEUE)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Received {}", event.eventId());
        notificationService.handleBookingCancelled(event);
    }
}
