package com.airline.flights.controller;

import com.airline.flights.dto.AirportRequest;
import com.airline.flights.dto.AirportResponse;
import com.airline.flights.dto.AirportUpdateRequest;
import com.airline.flights.dto.ApiResult;
import com.airline.flights.service.AirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/airports")
@RequiredArgsConstructor
@Tag(name = "Airports")
public class AirportController {

    private final AirportService airportService;

    @PostMapping
    @Operation(summary = "Create an airport")
    public ResponseEntity<ApiResult<AirportResponse>> create(@Valid @RequestBody AirportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully created the airport", airportService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List airports", description = "All airports, or only those in a city via ?cityId=")
    public ResponseEntity<ApiResult<List<AirportResponse>>> getAll(@RequestParam(required = false) Long cityId) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the airports", airportService.getAll(cityId)));
    }

    @GetMapping("/search")
    @Operation(summary = "Find airports by city name or airport name",
            description = "Partial, case-insensitive match on the airport name OR its city name. "
                    + "Results that start with the text are listed first. Example: /search?q=ben or /search?q=international")
    public ResponseEntity<ApiResult<List<AirportResponse>>> search(
            @Parameter(description = "Text to look for in the airport name or city name", example = "bengal")
            @RequestParam("q") String q,
            @Parameter(description = "Max results (1-50)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResult.ok("Successfully searched the airports", airportService.search(q, limit)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an airport by id")
    public ResponseEntity<ApiResult<AirportResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the airport", airportService.get(id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an airport (partial)")
    public ResponseEntity<ApiResult<AirportResponse>> update(@PathVariable Long id,
                                                             @Valid @RequestBody AirportUpdateRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Successfully updated the airport", airportService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an airport", description = "Fails with 409 while flights still use the airport")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        airportService.delete(id);
        return ResponseEntity.ok(ApiResult.ok("Successfully deleted the airport", null));
    }
}
