package com.airline.booking.dto;

import com.airline.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long userId,
        Long flightId,
        String flightNumber,
        String origin,
        String destination,
        Instant departureTime,
        int noOfSeats,
        BigDecimal unitPrice,
        BigDecimal totalCost,
        BookingStatus status,
        String failureReason,
        Instant createdAt) {
}
