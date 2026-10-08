package com.airline.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published when a booking is cancelled, so reminder-service can drop the booking's unsent emails and send a
 * cancellation notice. eventId is stable per booking.
 */
public record BookingCancelledEvent(String eventId, String type, Instant occurredAt, Data data) {

    public static final String TYPE = "BOOKING_CANCELLED";

    public record Data(Long bookingId, Long userId, String userEmail, Long flightId, String flightNumber,
                       String origin, String destination, Instant departureTime, int noOfSeats, BigDecimal totalCost) {
    }
}
