package com.airline.flights.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SeatRequest(
        @NotNull(message = "seats is required")
        @Min(value = 1, message = "seats must be at least 1")
        @Max(value = 1000, message = "seats must be at most 1000")
        Integer seats) {
}
