package com.airline.flights.dto;

public record AirportResponse(Long id, String name, String code, String address, Long cityId, String cityName) {
}
