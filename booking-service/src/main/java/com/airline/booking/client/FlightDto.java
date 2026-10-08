package com.airline.booking.client;

import java.math.BigDecimal;
import java.time.Instant;

/** Subset of flights-service's FlightResponse. Unknown JSON fields are ignored. */
public record FlightDto(
        Long id,
        String flightNumber,
        AirportDto departureAirport,
        AirportDto arrivalAirport,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal price,
        int totalSeats,
        int availableSeats) {
}
