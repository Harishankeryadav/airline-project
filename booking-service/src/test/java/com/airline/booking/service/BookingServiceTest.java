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
import com.airline.booking.exception.ServiceUnavailableException;
import com.airline.booking.repository.BookingRepository;
import com.airline.booking.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-10-20T09:00:00Z");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private FlightsGateway flights;
    @Mock
    private BookingEventPublisher eventPublisher;

    private BookingService service;

    private final AuthenticatedUser ana = new AuthenticatedUser(5L, "ana@example.com", List.of("CUSTOMER"));
    private final AuthenticatedUser admin = new AuthenticatedUser(1L, "admin@example.com", List.of("ADMIN", "CUSTOMER"));
    private final AuthenticatedUser bob = new AuthenticatedUser(9L, "bob@example.com", List.of("CUSTOMER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new BookingService(bookingRepository, flights, eventPublisher, clock);
    }

    private FlightDto flight(Instant departure, int availableSeats) {
        return new FlightDto(10L, "AI-101",
                new AirportDto(1L, "Kempegowda International Airport", "BLR", "Bengaluru"),
                new AirportDto(2L, "Indira Gandhi International Airport", "DEL", "Delhi"),
                departure, departure.plusSeconds(9000), new BigDecimal("5200.00"), 300, availableSeats);
    }

    private Booking storedBooking(BookingStatus status, Instant departure) {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setUserId(5L);
        booking.setUserEmail("ana@example.com");
        booking.setFlightId(10L);
        booking.setFlightNumber("AI-101");
        booking.setOrigin("Bengaluru (BLR)");
        booking.setDestination("Delhi (DEL)");
        booking.setDepartureTime(departure);
        booking.setNoOfSeats(2);
        booking.setUnitPrice(new BigDecimal("5200.00"));
        booking.setTotalCost(new BigDecimal("10400.00"));
        booking.setStatus(status);
        return booking;
    }

    /** repository.save(): give new rows an id, return the same object */
    private void saveAssignsId() {
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
    }

    // ------------------------------------------------------------------ create

    @Test
    void createReservesSeatsConfirmsAndPublishesTheEvent() {
        when(flights.getFlight(10L)).thenReturn(flight(FUTURE, 300));
        saveAssignsId();
        when(eventPublisher.publishConfirmed(any(Booking.class))).thenReturn(true);

        BookingResponse response = service.create(ana, new CreateBookingRequest(10L, 2));

        verify(flights).reserveSeats(10L, 2);
        verify(bookingRepository).markNotificationPublished(1L);
        assertEquals(BookingStatus.CONFIRMED, response.status());
        assertEquals(new BigDecimal("10400.00"), response.totalCost());
        assertEquals(5L, response.userId());                       // taken from the token
        assertEquals("Bengaluru (BLR)", response.origin());
        assertEquals("Delhi (DEL)", response.destination());
    }

    @Test
    void createStaysConfirmedEvenIfRabbitMqIsDown() {
        when(flights.getFlight(10L)).thenReturn(flight(FUTURE, 300));
        saveAssignsId();
        when(eventPublisher.publishConfirmed(any(Booking.class))).thenReturn(false);

        BookingResponse response = service.create(ana, new CreateBookingRequest(10L, 1));

        assertEquals(BookingStatus.CONFIRMED, response.status());
        verify(bookingRepository, never()).markNotificationPublished(any());   // the recovery job will retry
    }

    @Test
    void createMarksBookingFailedWhenFlightsServiceRejectsTheReservation() {
        when(flights.getFlight(10L)).thenReturn(flight(FUTURE, 300));
        saveAssignsId();
        when(flights.reserveSeats(10L, 2)).thenThrow(new ConflictException("Only 1 seat(s) left on flight AI-101, requested 2"));

        assertThrows(ConflictException.class, () -> service.create(ana, new CreateBookingRequest(10L, 2)));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(BookingStatus.FAILED, saved.getValue().getStatus());
        verify(eventPublisher, never()).publishConfirmed(any());
    }

    @Test
    void createRejectsAFlightThatHasAlreadyDeparted() {
        when(flights.getFlight(10L)).thenReturn(flight(NOW.minusSeconds(60), 300));

        assertThrows(BadRequestException.class, () -> service.create(ana, new CreateBookingRequest(10L, 1)));
        verify(bookingRepository, never()).save(any());
        verify(flights, never()).reserveSeats(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void createFailsFastWhenTheFlightObviouslyHasTooFewSeats() {
        when(flights.getFlight(10L)).thenReturn(flight(FUTURE, 1));

        assertThrows(ConflictException.class, () -> service.create(ana, new CreateBookingRequest(10L, 2)));
        verify(bookingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ read

    @Test
    void otherUsersBookingsLookLikeTheyDoNotExist() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(storedBooking(BookingStatus.CONFIRMED, FUTURE)));

        assertThrows(ResourceNotFoundException.class, () -> service.get(bob, 1L));
        assertEquals(1L, service.get(ana, 1L).id());
        assertEquals(1L, service.get(admin, 1L).id());
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancelGivesTheSeatsBack() {
        Booking booking = storedBooking(BookingStatus.CONFIRMED, FUTURE);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.transition(eq(1L), eq(BookingStatus.CONFIRMED), eq(BookingStatus.CANCELLED), eq(true), any(Instant.class)))
                .thenReturn(1);
        when(bookingRepository.setSeatReleasePending(eq(1L), eq(true), eq(false), any(Instant.class))).thenReturn(1);

        service.cancel(ana, 1L);

        verify(flights).releaseSeats(10L, 2);
        verify(eventPublisher).publishCancelled(any(Booking.class));   // reminder-service is told
    }

    @Test
    void cancelOnlyWorksForConfirmedBookings() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(storedBooking(BookingStatus.CANCELLED, FUTURE)));

        assertThrows(ConflictException.class, () -> service.cancel(ana, 1L));
        verify(flights, never()).releaseSeats(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void cancelIsRefusedAfterDeparture() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(storedBooking(BookingStatus.CONFIRMED, NOW.minusSeconds(60))));

        assertThrows(ConflictException.class, () -> service.cancel(ana, 1L));
    }

    @Test
    void aSecondConcurrentCancelDoesNotReleaseSeatsAgain() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(storedBooking(BookingStatus.CONFIRMED, FUTURE)));
        when(bookingRepository.transition(eq(1L), eq(BookingStatus.CONFIRMED), eq(BookingStatus.CANCELLED), eq(true), any(Instant.class)))
                .thenReturn(0);   // someone else already cancelled it

        assertThrows(ConflictException.class, () -> service.cancel(ana, 1L));
        verify(flights, never()).releaseSeats(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void cancelSucceedsAndKeepsTheReleasePendingWhenFlightsServiceIsDown() {
        Booking booking = storedBooking(BookingStatus.CONFIRMED, FUTURE);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.transition(eq(1L), eq(BookingStatus.CONFIRMED), eq(BookingStatus.CANCELLED), eq(true), any(Instant.class)))
                .thenReturn(1);
        when(bookingRepository.setSeatReleasePending(eq(1L), eq(true), eq(false), any(Instant.class))).thenReturn(1);
        doThrow(new ServiceUnavailableException("down")).when(flights).releaseSeats(10L, 2);

        service.cancel(ana, 1L);   // must not throw - the user's cancellation is recorded

        verify(bookingRepository).setSeatReleasePending(eq(1L), eq(false), eq(true), any(Instant.class)); // retried later
    }
}
