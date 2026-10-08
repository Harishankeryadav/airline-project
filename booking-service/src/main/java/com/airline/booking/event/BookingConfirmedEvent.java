package com.airline.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message published to RabbitMQ when a booking is confirmed (contract in docs/ARCHITECTURE.md).
 * eventId is stable per booking, so a consumer can ignore a duplicate if the event is ever re-sent.
 */
public record BookingConfirmedEvent(String eventId, String type, Instant occurredAt, Data data) {

    public static final String TYPE = "BOOKING_CONFIRMED";

    public record Data(Long bookingId, Long userId, String userEmail, Long flightId, String flightNumber,
                       String origin, String destination, Instant departureTime, int noOfSeats, BigDecimal totalCost) {
    }
}
