package com.airline.booking.client;

import com.airline.booking.dto.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for flights-service.
 * name = the Eureka name FLIGHTS-SERVICE; url = optional override (clients.flights.url / FLIGHTS_URL).
 * When url is empty the call is load-balanced through the registry.
 */
@FeignClient(name = "flights-service", url = "${clients.flights.url:}")
public interface FlightsClient {

    @GetMapping("/api/v1/flights/{id}")
    ApiResult<FlightDto> getFlight(@PathVariable("id") Long id);

    @PostMapping("/api/v1/flights/{id}/seats/reserve")
    ApiResult<FlightDto> reserveSeats(@PathVariable("id") Long id, @RequestBody SeatRequest request);

    @PostMapping("/api/v1/flights/{id}/seats/release")
    ApiResult<FlightDto> releaseSeats(@PathVariable("id") Long id, @RequestBody SeatRequest request);
}
