package com.airline.flights.controller;

import com.airline.flights.dto.ApiResult;
import com.airline.flights.dto.CityRequest;
import com.airline.flights.dto.CityResponse;
import com.airline.flights.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/city")
@RequiredArgsConstructor
@Tag(name = "Cities")
public class CityController {

    private final CityService cityService;

    @PostMapping
    @Operation(summary = "Create a city")
    public ResponseEntity<ApiResult<CityResponse>> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully created a city", cityService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List cities", description = "Optional ?name= filters by name prefix (case-insensitive)")
    public ResponseEntity<ApiResult<List<CityResponse>>> getAll(@RequestParam(required = false) String name) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched all cities", cityService.getAll(name)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a city by id")
    public ResponseEntity<ApiResult<CityResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the city", cityService.get(id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a city")
    public ResponseEntity<ApiResult<CityResponse>> update(@PathVariable Long id,
                                                          @Valid @RequestBody CityRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Successfully updated the city", cityService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a city", description = "Fails with 409 while the city still has airports")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        cityService.delete(id);
        return ResponseEntity.ok(ApiResult.ok("Successfully deleted the city", null));
    }
}
