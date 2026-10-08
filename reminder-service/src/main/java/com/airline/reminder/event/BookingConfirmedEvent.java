package com.airline.reminder.event;

import java.math.BigDecimal;
import java.time.Instant;

/** BOOKING_CONFIRMED from booking-service. Unknown JSON fields are ignored, so the producer can add fields safely. */
public record BookingConfirmedEvent(String eventId, String type, Instant occurredAt, Data data) {

    public record Data(Long bookingId, Long userId, String userEmail, Long flightId, String flightNumber,
                       String origin, String destination, Instant departureTime, int noOfSeats, BigDecimal totalCost) {
    }
}
