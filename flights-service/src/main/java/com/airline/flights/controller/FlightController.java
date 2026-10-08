package com.airline.flights.controller;

import com.airline.flights.dto.ApiResult;
import com.airline.flights.dto.FlightRequest;
import com.airline.flights.dto.FlightResponse;
import com.airline.flights.dto.FlightSearchCriteria;
import com.airline.flights.dto.FlightUpdateRequest;
import com.airline.flights.dto.SeatRequest;
import com.airline.flights.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Tag(name = "Flights")
public class FlightController {

    private final FlightService flightService;

    @PostMapping
    @Operation(summary = "Create a flight",
            description = "Seats are taken from the airplane's capacity. Times are ISO-8601 (e.g. 2026-11-01T09:30:00Z).")
    public ResponseEntity<ApiResult<FlightResponse>> create(@Valid @RequestBody FlightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully created a flight", flightService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Search flights",
            description = "All filters are optional and combinable. departureDate is a UTC calendar day. "
                    + "sortBy: departureTime | arrivalTime | price; order: asc | desc.")
    public ResponseEntity<ApiResult<List<FlightResponse>>> search(
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "yyyy-MM-dd (UTC)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @Parameter(description = "Only flights with at least this many seats left")
            @RequestParam(required = false) Integer seats,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        FlightSearchCriteria criteria = FlightSearchCriteria.of(departureAirportId, arrivalAirportId, minPrice,
                maxPrice, departureDate, seats, sortBy, order, page, size);
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the flights", flightService.search(criteria)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a flight by id")
    public ResponseEntity<ApiResult<FlightResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the flight", flightService.get(id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a flight (times, price, gate)",
            description = "Seat counts cannot be edited here - use the seats/reserve and seats/release endpoints.")
    public ResponseEntity<ApiResult<FlightResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody FlightUpdateRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Successfully updated the flight", flightService.update(id, request)));
    }

    @PostMapping("/{id}/seats/reserve")
    @Operation(summary = "Reserve seats (used by booking-service)",
            description = "Atomic: succeeds only if enough seats remain, otherwise 409. Safe under concurrent bookings.")
    public ResponseEntity<ApiResult<FlightResponse>> reserve(@PathVariable Long id,
                                                             @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Seats reserved", flightService.reserveSeats(id, request.seats())));
    }

    @PostMapping("/{id}/seats/release")
    @Operation(summary = "Release seats (used by booking-service on cancel / rollback)")
    public ResponseEntity<ApiResult<FlightResponse>> release(@PathVariable Long id,
                                                             @Valid @RequestBody SeatRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Seats released", flightService.releaseSeats(id, request.seats())));
    }
}
