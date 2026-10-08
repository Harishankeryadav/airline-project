package com.airline.flights.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update - only non-null fields are applied. */
public record AirportUpdateRequest(
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "code must be a 3-letter IATA code")
        String code,

        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        Long cityId) {
}
