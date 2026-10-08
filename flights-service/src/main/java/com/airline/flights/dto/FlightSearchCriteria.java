package com.airline.flights.dto;

import com.airline.flights.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Normalised, validated search filters. Because defaults are applied in {@link #of}, two equivalent requests
 * produce an identical toString(), which the Redis cache uses as its key.
 */
public record FlightSearchCriteria(
        Long departureAirportId,
        Long arrivalAirportId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate departureDate,   // interpreted as a UTC calendar day
        Integer seats,             // only flights with at least this many seats left
        String sortBy,
        String order,
        int page,
        int size) {

    private static final Set<String> SORTABLE = Set.of("departureTime", "arrivalTime", "price");
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    public static FlightSearchCriteria of(Long departureAirportId, Long arrivalAirportId,
                                          BigDecimal minPrice, BigDecimal maxPrice,
                                          LocalDate departureDate, Integer seats,
                                          String sortBy, String order, Integer page, Integer size) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new BadRequestException("minPrice cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        if (seats != null && seats < 1) {
            throw new BadRequestException("seats must be at least 1");
        }
        String sort = (sortBy == null || sortBy.isBlank()) ? "departureTime" : sortBy.trim();
        if (!SORTABLE.contains(sort)) {
            throw new BadRequestException("sortBy must be one of " + SORTABLE);
        }
        String dir = (order == null || order.isBlank()) ? "asc" : order.trim().toLowerCase();
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new BadRequestException("order must be 'asc' or 'desc'");
        }
        int p = page == null ? 0 : Math.max(page, 0);
        int s = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return new FlightSearchCriteria(departureAirportId, arrivalAirportId, minPrice, maxPrice,
                departureDate, seats, sort, dir, p, s);
    }
}
