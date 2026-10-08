package com.airline.booking.service;

import com.airline.booking.client.AirportDto;
import com.airline.booking.client.FlightDto;
import com.airline.booking.client.FlightsGateway;
import com.airline.booking.dto.BookingResponse;
import com.airline.booking.dto.CreateBookingRequest;
import com.airline.booking.entity.Booking;
import com.airline.booking.entity.BookingStatus;
import com.airline.booking.event.BookingEventPublisher;
import com.airline.booking.exception.BadRequestException;
import com.airline.booking.exception.ConflictException;
import com.airline.booking.exception.ResourceNotFoundException;
import com.airline.booking.repository.BookingRepository;
import com.airline.booking.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Booking flow (replaces the Node "read flight, then PATCH the seat count" approach):
 * <pre>
 *   1. GET flight from flights-service (Feign)            -> price, departure, quick availability check
 *   2. save booking as PENDING
 *   3. POST seats/reserve on flights-service (Feign)      -> atomic; 409 if not enough seats  -> booking FAILED
 *   4. save booking as CONFIRMED
 *   5. publish BOOKING_CONFIRMED to RabbitMQ (retried by BookingRecoveryJob if the broker is down)
 * Cancelling publishes BOOKING_CANCELLED (best effort).
 * </pre>
 * No database transaction is held open across the remote calls on purpose: each repository call commits on its own,
 * so a slow flights-service can never hold database locks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightsGateway flights;
    private final BookingEventPublisher eventPublisher;
    private final Clock clock;

    public BookingResponse create(AuthenticatedUser user, CreateBookingRequest request) {
        int seats = request.noOfSeats();
        FlightDto flight = flights.getFlight(request.flightId());

        if (!flight.departureTime().isAfter(clock.instant())) {
            throw new BadRequestException("This flight has already departed");
        }
        if (flight.availableSeats() < seats) {
            throw new ConflictException("Only " + flight.availableSeats() + " seat(s) left on flight "
                    + flight.flightNumber() + ", requested " + seats);
        }

        Booking booking = new Booking();
        booking.setUserId(user.id());
        booking.setUserEmail(user.email());
        booking.setFlightId(flight.id());
        booking.setFlightNumber(flight.flightNumber());
        booking.setOrigin(describe(flight.departureAirport()));
        booking.setDestination(describe(flight.arrivalAirport()));
        booking.setDepartureTime(flight.departureTime());
        booking.setNoOfSeats(seats);
        booking.setUnitPrice(flight.price());
        booking.setTotalCost(flight.price().multiply(BigDecimal.valueOf(seats)));
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);

        try {
            flights.reserveSeats(flight.id(), seats);   // atomic on the flights side; the real availability check
        } catch (RuntimeException e) {
            markFailed(booking, e.getMessage());
            throw e;
        }

        try {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking = bookingRepository.save(booking);
        } catch (RuntimeException e) {
            log.error("Seats were reserved but booking {} could not be confirmed - giving the seats back", booking.getId(), e);
            try {
                flights.releaseSeats(flight.id(), seats);
            } catch (RuntimeException releaseFailure) {
                log.error("MANUAL ATTENTION: {} seat(s) on flight {} could not be released after a failed booking {}",
                        seats, flight.id(), booking.getId(), releaseFailure);
            }
            markFailed(booking, "Could not save the confirmed booking");
            throw e;
        }

        publishConfirmedEvent(booking);
        return toResponse(booking);
    }

    public BookingResponse get(AuthenticatedUser user, Long id) {
        return toResponse(findVisible(user, id));
    }

    public List<BookingResponse> listMine(AuthenticatedUser user) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.id()).stream().map(BookingService::toResponse).toList();
    }

    /** ADMIN: every booking, optionally filtered by status, newest first. */
    public List<BookingResponse> listAll(BookingStatus status) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Booking> bookings = status == null ? bookingRepository.findAll(newestFirst) : bookingRepository.findByStatus(status, newestFirst);
        return bookings.stream().map(BookingService::toResponse).toList();
    }

    /**
     * Cancels a CONFIRMED booking before departure and gives the seats back.
     * The status change is a compare-and-set, so a double click or two concurrent cancels cannot release seats twice.
     */
    public BookingResponse cancel(AuthenticatedUser user, Long id) {
        Booking booking = findVisible(user, id);
        Instant now = clock.instant();

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only CONFIRMED bookings can be cancelled (this one is " + booking.getStatus() + ")");
        }
        if (!booking.getDepartureTime().isAfter(now)) {
            throw new ConflictException("The flight has already departed, so this booking can no longer be cancelled");
        }
        if (bookingRepository.transition(id, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, true, now) == 0) {
            throw new ConflictException("This booking was changed by another request - please reload it");
        }

        tryReleaseSeats(booking);
        Booking cancelled = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        eventPublisher.publishCancelled(cancelled);   // lets reminder-service drop pending emails and notify the customer
        return toResponse(cancelled);
    }

    // ------------------------------------------------------------ used by BookingRecoveryJob too

    /** @return false if RabbitMQ was unreachable (the recovery job tries again later) */
    public boolean publishConfirmedEvent(Booking booking) {
        try {
            if (eventPublisher.publishConfirmed(booking)) {
                bookingRepository.markNotificationPublished(booking.getId());
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Could not record the published event for booking {}: {}", booking.getId(), e.getMessage());
        }
        return false;
    }

    /**
     * Gives a cancelled booking's seats back to flights-service. Claims the work first (compare-and-set on
     * seat_release_pending) so it happens exactly once even with several booking-service instances; if flights-service
     * is down the flag is restored and the recovery job retries.
     */
    public void tryReleaseSeats(Booking booking) {
        if (bookingRepository.setSeatReleasePending(booking.getId(), true, false, clock.instant()) == 0) {
            return; // already released, or being handled by someone else
        }
        try {
            flights.releaseSeats(booking.getFlightId(), booking.getNoOfSeats());
        } catch (BadRequestException | ResourceNotFoundException e) {
            // Retrying cannot help (flight gone, or the seats are already back). Leave the flag cleared.
            log.error("MANUAL ATTENTION: seats for cancelled booking {} could not be released: {}", booking.getId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Releasing seats for booking {} failed, will retry: {}", booking.getId(), e.getMessage());
            bookingRepository.setSeatReleasePending(booking.getId(), false, true, clock.instant());
        }
    }

    // ------------------------------------------------------------ helpers

    private Booking findVisible(AuthenticatedUser user, Long id) {
        // Someone else's booking is reported as "not found" so ids cannot be used to discover other users' bookings.
        return bookingRepository.findById(id)
                .filter(b -> user.isAdmin() || b.getUserId().equals(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
    }

    private void markFailed(Booking booking, String reason) {
        try {
            booking.setStatus(BookingStatus.FAILED);
            booking.setFailureReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 255)));
            bookingRepository.save(booking);
        } catch (RuntimeException e) {
            log.error("Could not mark booking {} as FAILED", booking.getId(), e);
        }
    }

    private static String describe(AirportDto airport) {
        String place = airport.cityName() != null ? airport.cityName() : airport.name();
        return airport.code() != null ? place + " (" + airport.code() + ")" : place;
    }

    static BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getUserId(), b.getFlightId(), b.getFlightNumber(), b.getOrigin(),
                b.getDestination(), b.getDepartureTime(), b.getNoOfSeats(), b.getUnitPrice(), b.getTotalCost(),
                b.getStatus(), b.getFailureReason(), b.getCreatedAt());
    }
}
