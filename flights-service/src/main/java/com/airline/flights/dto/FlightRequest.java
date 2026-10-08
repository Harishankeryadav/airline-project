package com.airline.flights.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record FlightRequest(
        @NotBlank(message = "flightNumber is required")
        @Size(max = 20, message = "flightNumber must be at most 20 characters")
        String flightNumber,

        @NotNull(message = "airplaneId is required")
        Long airplaneId,

        @NotNull(message = "departureAirportId is required")
        Long departureAirportId,

        @NotNull(message = "arrivalAirportId is required")
        Long arrivalAirportId,

        @NotNull(message = "departureTime is required (ISO-8601, e.g. 2026-11-01T09:30:00Z)")
        Instant departureTime,

        @NotNull(message = "arrivalTime is required (ISO-8601, e.g. 2026-11-01T12:15:00Z)")
        Instant arrivalTime,

        @NotNull(message = "price is required")
        @Positive(message = "price must be greater than 0")
        BigDecimal price,

        @Size(max = 20, message = "boardingGate must be at most 20 characters")
        String boardingGate) {
}
