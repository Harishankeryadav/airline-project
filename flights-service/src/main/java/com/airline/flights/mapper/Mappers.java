package com.airline.flights.mapper;

import com.airline.flights.dto.AirplaneResponse;
import com.airline.flights.dto.AirportResponse;
import com.airline.flights.dto.CityResponse;
import com.airline.flights.dto.FlightResponse;
import com.airline.flights.entity.Airplane;
import com.airline.flights.entity.Airport;
import com.airline.flights.entity.City;
import com.airline.flights.entity.Flight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Entity -> DTO mapping. Call inside a transaction (associations are lazy). */
public final class Mappers {

    private Mappers() {
    }

    public static CityResponse toCityResponse(City city) {
        return new CityResponse(city.getId(), city.getName());
    }

    public static AirportResponse toAirportResponse(Airport airport) {
        City city = airport.getCity();
        return new AirportResponse(airport.getId(), airport.getName(), airport.getCode(), airport.getAddress(),
                city.getId(), city.getName());
    }

    public static AirplaneResponse toAirplaneResponse(Airplane airplane) {
        return new AirplaneResponse(airplane.getId(), airplane.getModelNumber(), airplane.getCapacity());
    }

    public static FlightResponse toFlightResponse(Flight f) {
        return new FlightResponse(
                f.getId(),
                f.getFlightNumber(),
                f.getAirplane().getId(),
                f.getAirplane().getModelNumber(),
                f.getDepartureAirport().getId(),
                f.getArrivalAirport().getId(),
                toAirportResponse(f.getDepartureAirport()),
                toAirportResponse(f.getArrivalAirport()),
                f.getDepartureTime(),
                f.getArrivalTime(),
                f.getPrice(),
                f.getBoardingGate(),
                f.getTotalSeats(),
                f.getAvailableSeats());
    }

    /** Always returns a plain ArrayList - keeps Redis (de)serialisation of cached lists simple and safe. */
    public static <S, T> List<T> mapAll(Collection<S> source, Function<S, T> mapper) {
        List<T> result = new ArrayList<>(source.size());
        for (S item : source) {
            result.add(mapper.apply(item));
        }
        return result;
    }
}
