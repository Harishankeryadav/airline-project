package com.airline.flights.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Partial update - only non-null fields are applied.
 * Seat counts are deliberately NOT editable here; they change only through the atomic reserve/release endpoints.
 */
public record FlightUpdateRequest(
        Instant departureTime,
        Instant arrivalTime,

        @Positive(message = "price must be greater than 0")
        BigDecimal price,

        @Size(max = 20, message = "boardingGate must be at most 20 characters")
        String boardingGate) {
}
