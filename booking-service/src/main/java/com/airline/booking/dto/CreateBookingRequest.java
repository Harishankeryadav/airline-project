package com.airline.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Note: no userId - the booking always belongs to the signed-in user from the token. */
public record CreateBookingRequest(
        @NotNull(message = "flightId is required")
        Long flightId,

        @NotNull(message = "noOfSeats is required")
        @Min(value = 1, message = "noOfSeats must be at least 1")
        @Max(value = 9, message = "You can book at most 9 seats at once")
        Integer noOfSeats) {
}
