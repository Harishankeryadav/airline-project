package com.airline.flights.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AirportRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "code must be a 3-letter IATA code")
        String code,

        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        @NotNull(message = "cityId is required")
        Long cityId) {
}
