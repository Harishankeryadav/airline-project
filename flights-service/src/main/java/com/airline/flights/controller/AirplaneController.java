package com.airline.flights.controller;

import com.airline.flights.dto.AirplaneRequest;
import com.airline.flights.dto.AirplaneResponse;
import com.airline.flights.dto.ApiResult;
import com.airline.flights.service.AirplaneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/airplanes")
@RequiredArgsConstructor
@Tag(name = "Airplanes")
public class AirplaneController {

    private final AirplaneService airplaneService;

    @PostMapping
    @Operation(summary = "Create an airplane", description = "capacity defaults to 200 when omitted")
    public ResponseEntity<ApiResult<AirplaneResponse>> create(@Valid @RequestBody AirplaneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully created the airplane", airplaneService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List airplanes")
    public ResponseEntity<ApiResult<List<AirplaneResponse>>> getAll() {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the airplanes", airplaneService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an airplane by id")
    public ResponseEntity<ApiResult<AirplaneResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the airplane", airplaneService.get(id)));
    }
}
