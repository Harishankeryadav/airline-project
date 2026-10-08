package com.airline.flights.service;

import com.airline.flights.config.CacheNames;
import com.airline.flights.dto.FlightRequest;
import com.airline.flights.dto.FlightResponse;
import com.airline.flights.dto.FlightSearchCriteria;
import com.airline.flights.dto.FlightUpdateRequest;
import com.airline.flights.entity.Airplane;
import com.airline.flights.entity.Airport;
import com.airline.flights.entity.Flight;
import com.airline.flights.exception.BadRequestException;
import com.airline.flights.exception.DuplicateResourceException;
import com.airline.flights.exception.InsufficientSeatsException;
import com.airline.flights.exception.ResourceNotFoundException;
import com.airline.flights.mapper.Mappers;
import com.airline.flights.repository.AirplaneRepository;
import com.airline.flights.repository.AirportRepository;
import com.airline.flights.repository.FlightRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final AirplaneRepository airplaneRepository;
    private final AirportRepository airportRepository;

    @Transactional
    @CacheEvict(cacheNames = CacheNames.FLIGHTS, allEntries = true)
    public FlightResponse create(FlightRequest request) {
        validateTimes(request.departureTime(), request.arrivalTime());
        if (request.departureAirportId().equals(request.arrivalAirportId())) {
            throw new BadRequestException("Departure and arrival airport must be different");
        }
        String flightNumber = request.flightNumber().trim().toUpperCase();
        if (flightRepository.existsByFlightNumberIgnoreCase(flightNumber)) {
            throw new DuplicateResourceException("Flight number already exists: " + flightNumber);
        }

        Airplane airplane = airplaneRepository.findById(request.airplaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Airplane not found with id " + request.airplaneId()));
        Airport departure = findAirport(request.departureAirportId());
        Airport arrival = findAirport(request.arrivalAirportId());

        Flight flight = new Flight();
        flight.setFlightNumber(flightNumber);
        flight.setAirplane(airplane);
        flight.setDepartureAirport(departure);
        flight.setArrivalAirport(arrival);
        flight.setDepartureTime(request.departureTime());
        flight.setArrivalTime(request.arrivalTime());
        flight.setPrice(request.price());
        flight.setBoardingGate(request.boardingGate());
        flight.setTotalSeats(airplane.getCapacity());
        flight.setAvailableSeats(airplane.getCapacity());
        return Mappers.toFlightResponse(flightRepository.save(flight));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.FLIGHT, key = "#id")
    public FlightResponse get(Long id) {
        return Mappers.toFlightResponse(findWithDetails(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.FLIGHTS, key = "#criteria.toString()")
    public List<FlightResponse> search(FlightSearchCriteria criteria) {
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.order()), criteria.sortBy()).and(Sort.by("id"));
        Page<Flight> page = flightRepository.findAll(toSpecification(criteria),
                PageRequest.of(criteria.page(), criteria.size(), sort));
        return Mappers.mapAll(page.getContent(), Mappers::toFlightResponse);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLIGHT, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.FLIGHTS, allEntries = true)
    })
    public FlightResponse update(Long id, FlightUpdateRequest request) {
        Flight flight = findWithDetails(id);
        Instant departure = request.departureTime() != null ? request.departureTime() : flight.getDepartureTime();
        Instant arrival = request.arrivalTime() != null ? request.arrivalTime() : flight.getArrivalTime();
        validateTimes(departure, arrival);

        flight.setDepartureTime(departure);
        flight.setArrivalTime(arrival);
        if (request.price() != null) {
            flight.setPrice(request.price());
        }
        if (request.boardingGate() != null) {
            flight.setBoardingGate(request.boardingGate());
        }
        return Mappers.toFlightResponse(flight);
    }

    /**
     * Atomically takes {@code seats} seats. Used by the booking service.
     * @throws InsufficientSeatsException (409) if fewer seats remain
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLIGHT, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.FLIGHTS, allEntries = true)
    })
    public FlightResponse reserveSeats(Long id, int seats) {
        int updated = flightRepository.reserveSeats(id, seats);
        if (updated == 0) {
            Flight flight = flightRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id " + id));
            throw new InsufficientSeatsException("Only " + flight.getAvailableSeats()
                    + " seat(s) left on flight " + flight.getFlightNumber() + ", requested " + seats);
        }
        return Mappers.toFlightResponse(findWithDetails(id));
    }

    /** Gives seats back (cancelled booking, or compensation after a failed booking). */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.FLIGHT, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.FLIGHTS, allEntries = true)
    })
    public FlightResponse releaseSeats(Long id, int seats) {
        int updated = flightRepository.releaseSeats(id, seats);
        if (updated == 0) {
            if (!flightRepository.existsById(id)) {
                throw new ResourceNotFoundException("Flight not found with id " + id);
            }
            throw new BadRequestException("Cannot release " + seats + " seat(s): it would exceed the flight's capacity");
        }
        return Mappers.toFlightResponse(findWithDetails(id));
    }

    // ------------------------------------------------------------------ helpers

    private Flight findWithDetails(Long id) {
        return flightRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found with id " + id));
    }

    private Airport findAirport(Long id) {
        return airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found with id " + id));
    }

    private static void validateTimes(Instant departure, Instant arrival) {
        if (!arrival.isAfter(departure)) {
            throw new BadRequestException("Arrival time must be after departure time");
        }
    }

    private static Specification<Flight> toSpecification(FlightSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.departureAirportId() != null) {
                predicates.add(cb.equal(root.get("departureAirport").get("id"), c.departureAirportId()));
            }
            if (c.arrivalAirportId() != null) {
                predicates.add(cb.equal(root.get("arrivalAirport").get("id"), c.arrivalAirportId()));
            }
            if (c.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), c.maxPrice()));
            }
            if (c.departureDate() != null) {
                Instant start = c.departureDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant end = start.plus(1, ChronoUnit.DAYS);
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("departureTime"), start));
                predicates.add(cb.lessThan(root.<Instant>get("departureTime"), end));
            }
            if (c.seats() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Integer>get("availableSeats"), c.seats()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
