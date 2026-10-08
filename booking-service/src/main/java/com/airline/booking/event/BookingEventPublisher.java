package com.airline.booking.event;

import com.airline.booking.config.RabbitConfig;
import com.airline.booking.entity.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    /** @return true if RabbitMQ accepted the message. Never throws: a broker outage must not fail a paid booking. */
    public boolean publishConfirmed(Booking booking) {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                "booking-confirmed-" + booking.getId(),
                BookingConfirmedEvent.TYPE,
                clock.instant(),
                new BookingConfirmedEvent.Data(booking.getId(), booking.getUserId(), booking.getUserEmail(),
                        booking.getFlightId(), booking.getFlightNumber(), booking.getOrigin(), booking.getDestination(),
                        booking.getDepartureTime(), booking.getNoOfSeats(), booking.getTotalCost()));
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
            return true;
        } catch (AmqpException e) {
            log.warn("Could not publish BOOKING_CONFIRMED for booking {} (will be retried): {}", booking.getId(), e.getMessage());
            return false;
        }
    }

    /** Best effort: a broker outage must not undo a cancellation. (Worst case the customer gets no cancellation email.) */
    public boolean publishCancelled(Booking booking) {
        BookingCancelledEvent event = new BookingCancelledEvent(
                "booking-cancelled-" + booking.getId(),
                BookingCancelledEvent.TYPE,
                clock.instant(),
                new BookingCancelledEvent.Data(booking.getId(), booking.getUserId(), booking.getUserEmail(),
                        booking.getFlightId(), booking.getFlightNumber(), booking.getOrigin(), booking.getDestination(),
                        booking.getDepartureTime(), booking.getNoOfSeats(), booking.getTotalCost()));
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CANCELLED_ROUTING_KEY, event);
            return true;
        } catch (AmqpException e) {
            log.warn("Could not publish BOOKING_CANCELLED for booking {}: {}", booking.getId(), e.getMessage());
            return false;
        }
    }
}
