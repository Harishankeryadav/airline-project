package com.airline.flights.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FlightResponse(
        Long id,
        String flightNumber,
        Long airplaneId,
        String airplaneModel,
        Long departureAirportId,
        Long arrivalAirportId,
        AirportResponse departureAirport,
        AirportResponse arrivalAirport,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal price,
        String boardingGate,
        int totalSeats,
        int availableSeats) {
}
