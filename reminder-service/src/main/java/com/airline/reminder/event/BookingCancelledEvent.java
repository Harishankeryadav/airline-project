package com.airline.reminder.event;

import java.math.BigDecimal;
import java.time.Instant;

/** BOOKING_CANCELLED from booking-service. */
public record BookingCancelledEvent(String eventId, String type, Instant occurredAt, Data data) {

    public record Data(Long bookingId, Long userId, String userEmail, Long flightId, String flightNumber,
                       String origin, String destination, Instant departureTime, int noOfSeats, BigDecimal totalCost) {
    }
}
