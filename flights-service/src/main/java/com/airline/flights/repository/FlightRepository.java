package com.airline.flights.repository;

import com.airline.flights.entity.Flight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long>, JpaSpecificationExecutor<Flight> {

    boolean existsByFlightNumberIgnoreCase(String flightNumber);

    @EntityGraph(attributePaths = {"airplane", "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city"})
    Optional<Flight> findWithDetailsById(Long id);

    /** Search with all associations loaded in the same query (avoids N+1 when mapping to DTOs). */
    @Override
    @EntityGraph(attributePaths = {"airplane", "departureAirport", "departureAirport.city",
            "arrivalAirport", "arrivalAirport.city"})
    Page<Flight> findAll(Specification<Flight> spec, Pageable pageable);

    /**
     * Atomic seat reservation: a single UPDATE that only succeeds while enough seats remain.
     * Returns the number of rows changed (1 = reserved, 0 = not enough seats or no such flight).
     * This replaces the Node version's read-then-PATCH, which could oversell under concurrent bookings.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Flight f set f.availableSeats = f.availableSeats - :seats "
            + "where f.id = :id and f.availableSeats >= :seats")
    int reserveSeats(@Param("id") Long id, @Param("seats") int seats);

    /** Atomic seat release (booking cancelled / failed). Never exceeds the flight's total seats. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Flight f set f.availableSeats = f.availableSeats + :seats "
            + "where f.id = :id and f.availableSeats + :seats <= f.totalSeats")
    int releaseSeats(@Param("id") Long id, @Param("seats") int seats);
}
