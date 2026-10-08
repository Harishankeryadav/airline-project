package com.airline.reminder.service;

import com.airline.reminder.config.ReminderProperties;
import com.airline.reminder.dto.CreateNotificationRequest;
import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.event.BookingCancelledEvent;
import com.airline.reminder.event.BookingConfirmedEvent;
import com.airline.reminder.exception.ConflictException;
import com.airline.reminder.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, new ReminderProperties(24, 5, 5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BookingConfirmedEvent confirmed(Instant departure) {
        return new BookingConfirmedEvent("booking-confirmed-12", "BOOKING_CONFIRMED", NOW,
                new BookingConfirmedEvent.Data(12L, 7L, "ana@example.com", 3L, "AI-101", "Bengaluru (BLR)",
                        "Delhi (DEL)", departure, 2, new BigDecimal("10400.00")));
    }

    private BookingCancelledEvent cancelled() {
        return new BookingCancelledEvent("booking-cancelled-12", "BOOKING_CANCELLED", NOW,
                new BookingCancelledEvent.Data(12L, 7L, "ana@example.com", 3L, "AI-101", "Bengaluru (BLR)",
                        "Delhi (DEL)", NOW.plus(Duration.ofDays(3)), 2, new BigDecimal("10400.00")));
    }

    @Test
    void confirmedEventCreatesAConfirmationNowAndAReminder24HoursBeforeDeparture() {
        Instant departure = NOW.plus(Duration.ofDays(3));

        service.handleBookingConfirmed(confirmed(departure));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        Notification confirmation = saved.getAllValues().get(0);
        Notification reminder = saved.getAllValues().get(1);

        assertEquals(NotificationKind.BOOKING_CONFIRMATION, confirmation.getKind());
        assertEquals(NOW, confirmation.getNotificationTime());
        assertEquals("ana@example.com", confirmation.getRecipientEmail());
        assertEquals(NotificationStatus.PENDING, confirmation.getStatus());
        assertEquals(12L, confirmation.getBookingId());
        assertTrue(confirmation.getSubject().contains("AI-101"));
        assertTrue(confirmation.getContent().contains("Bengaluru (BLR) -> Delhi (DEL)"));
        assertTrue(confirmation.getContent().contains("10400.00"));

        assertEquals(NotificationKind.DEPARTURE_REMINDER, reminder.getKind());
        assertEquals(departure.minus(Duration.ofHours(24)), reminder.getNotificationTime());
        assertEquals("booking-confirmed-12", reminder.getSourceEventId());
    }

    @Test
    void noSeparateReminderWhenBookedLessThan24HoursBeforeDeparture() {
        service.handleBookingConfirmed(confirmed(NOW.plus(Duration.ofHours(5))));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(saved.capture());
        assertEquals(NotificationKind.BOOKING_CONFIRMATION, saved.getValue().getKind());
    }

    @Test
    void aRedeliveredEventCreatesNothing() {
        when(repository.existsBySourceEventIdAndKind(eq("booking-confirmed-12"), any(NotificationKind.class))).thenReturn(true);

        service.handleBookingConfirmed(confirmed(NOW.plus(Duration.ofDays(3))));

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void aConfirmationThatArrivesAfterTheCancellationIsIgnored() {
        when(repository.existsByBookingIdAndKind(12L, NotificationKind.BOOKING_CANCELLATION)).thenReturn(true);

        service.handleBookingConfirmed(confirmed(NOW.plus(Duration.ofDays(3))));

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void cancelledEventDropsUnsentEmailsAndSendsACancellationNotice() {
        service.handleBookingCancelled(cancelled());

        verify(repository).cancelPendingForBooking(eq(12L), eq(NotificationStatus.PENDING), eq(NotificationStatus.CANCELLED),
                eq(List.of(NotificationKind.BOOKING_CONFIRMATION, NotificationKind.DEPARTURE_REMINDER)), any(Instant.class));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        assertEquals(NotificationKind.BOOKING_CANCELLATION, saved.getValue().getKind());
        assertEquals("booking-cancelled-12", saved.getValue().getSourceEventId());
    }

    @Test
    void anEventWithoutDataIsRejectedSoItEndsUpInTheDeadLetterQueue() {
        BookingConfirmedEvent broken = new BookingConfirmedEvent("x", "BOOKING_CONFIRMED", NOW, null);

        assertThrows(IllegalArgumentException.class, () -> service.handleBookingConfirmed(broken));
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void customNotificationDefaultsToSendNow() {
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createCustom(new CreateNotificationRequest("Hello", "Body", " ops@example.com ", null));

        assertEquals(NotificationKind.CUSTOM, response.kind());
        assertEquals(NOW, response.notificationTime());
        assertEquals("ops@example.com", response.recipientEmail());
        assertEquals(NotificationStatus.PENDING, response.status());
    }

    @Test
    void onlyPendingNotificationsCanBeCancelled() {
        Notification sent = new Notification();
        sent.setId(5L);
        sent.setStatus(NotificationStatus.SENT);
        sent.setNotificationTime(NOW);
        when(repository.findById(5L)).thenReturn(Optional.of(sent));
        when(repository.transition(eq(5L), eq(NotificationStatus.PENDING), eq(NotificationStatus.CANCELLED),
                eq(0), eq(NOW), any(Instant.class))).thenReturn(0);

        assertThrows(ConflictException.class, () -> service.cancel(5L));
    }
}
