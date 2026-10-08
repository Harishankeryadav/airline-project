package com.airline.flights.service;

import com.airline.flights.dto.FlightRequest;
import com.airline.flights.dto.FlightResponse;
import com.airline.flights.entity.Airplane;
import com.airline.flights.entity.Airport;
import com.airline.flights.entity.City;
import com.airline.flights.entity.Flight;
import com.airline.flights.exception.BadRequestException;
import com.airline.flights.exception.InsufficientSeatsException;
import com.airline.flights.exception.ResourceNotFoundException;
import com.airline.flights.repository.AirplaneRepository;
import com.airline.flights.repository.AirportRepository;
import com.airline.flights.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private AirplaneRepository airplaneRepository;
    @Mock
    private AirportRepository airportRepository;
    @InjectMocks
    private FlightService flightService;

    private static final Instant DEPARTURE = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2026-11-01T11:30:00Z");

    private FlightRequest request(Instant dep, Instant arr, Long fromAirport, Long toAirport) {
        return new FlightRequest("ai-101", 1L, fromAirport, toAirport, dep, arr, new BigDecimal("5200.00"), "A12");
    }

    private Airport airport(long id, String name, String cityName) {
        City city = new City(cityName);
        city.setId(id);
        Airport airport = new Airport();
        airport.setId(id);
        airport.setName(name);
        airport.setCity(city);
        return airport;
    }

    @Test
    void createRejectsArrivalNotAfterDeparture() {
        assertThrows(BadRequestException.class,
                () -> flightService.create(request(ARRIVAL, DEPARTURE, 10L, 20L)));
        assertThrows(BadRequestException.class,
                () -> flightService.create(request(DEPARTURE, DEPARTURE, 10L, 20L)));
    }

    @Test
    void createRejectsSameDepartureAndArrivalAirport() {
        assertThrows(BadRequestException.class,
                () -> flightService.create(request(DEPARTURE, ARRIVAL, 10L, 10L)));
    }

    @Test
    void createTakesSeatsFromAirplaneCapacityAndNormalisesFlightNumber() {
        Airplane airplane = new Airplane();
        airplane.setId(1L);
        airplane.setModelNumber("Boeing 737");
        airplane.setCapacity(300);

        when(flightRepository.existsByFlightNumberIgnoreCase("AI-101")).thenReturn(false);
        when(airplaneRepository.findById(1L)).thenReturn(Optional.of(airplane));
        when(airportRepository.findById(10L)).thenReturn(Optional.of(airport(10L, "Kempegowda International Airport", "Bengaluru")));
        when(airportRepository.findById(20L)).thenReturn(Optional.of(airport(20L, "Indira Gandhi International Airport", "Delhi")));
        when(flightRepository.save(any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponse response = flightService.create(request(DEPARTURE, ARRIVAL, 10L, 20L));

        ArgumentCaptor<Flight> saved = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepository).save(saved.capture());
        assertEquals("AI-101", saved.getValue().getFlightNumber());
        assertEquals(300, saved.getValue().getTotalSeats());
        assertEquals(300, saved.getValue().getAvailableSeats());
        assertEquals(300, response.availableSeats());
        assertEquals("Bengaluru", response.departureAirport().cityName());
    }

    @Test
    void reserveSeatsFailsWithConflictWhenNotEnoughSeatsLeft() {
        Flight flight = new Flight();
        flight.setId(1L);
        flight.setFlightNumber("AI-101");
        flight.setAvailableSeats(2);

        when(flightRepository.reserveSeats(1L, 5)).thenReturn(0);
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));

        InsufficientSeatsException ex = assertThrows(InsufficientSeatsException.class,
                () -> flightService.reserveSeats(1L, 5));
        assertEquals("Only 2 seat(s) left on flight AI-101, requested 5", ex.getMessage());
    }

    @Test
    void reserveSeatsFailsWithNotFoundForUnknownFlight() {
        when(flightRepository.reserveSeats(99L, 1)).thenReturn(0);
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> flightService.reserveSeats(99L, 1));
    }

    @Test
    void releaseSeatsRejectsOverCapacityRelease() {
        when(flightRepository.releaseSeats(1L, 10)).thenReturn(0);
        when(flightRepository.existsById(1L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> flightService.releaseSeats(1L, 10));
    }
}
