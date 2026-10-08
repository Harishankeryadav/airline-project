package com.airline.flights.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AirplaneRequest(
        @NotBlank(message = "modelNumber is required")
        @Size(max = 100, message = "modelNumber must be at most 100 characters")
        String modelNumber,

        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 1000, message = "capacity must be at most 1000")
        Integer capacity) {
}
