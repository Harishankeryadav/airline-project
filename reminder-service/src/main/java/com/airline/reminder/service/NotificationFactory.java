package com.airline.reminder.service;

import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.event.BookingCancelledEvent;
import com.airline.reminder.event.BookingConfirmedEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Builds the email text for each kind of notification. Plain text, ASCII only, so it renders everywhere. */
final class NotificationFactory {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private NotificationFactory() {
    }

    static Notification confirmation(BookingConfirmedEvent event, Instant sendAt) {
        BookingConfirmedEvent.Data d = event.data();
        String subject = "Booking confirmed - " + d.flightNumber() + " from " + d.origin() + " to " + d.destination();
        String content = """
                Hello,

                Your booking #%d is confirmed.

                Flight:     %s
                Route:      %s -> %s
                Departure:  %s
                Seats:      %d
                Total:      %s

                We will remind you before departure. Have a pleasant journey!
                """.formatted(d.bookingId(), d.flightNumber(), d.origin(), d.destination(),
                WHEN.format(d.departureTime()), d.noOfSeats(), d.totalCost().toPlainString());
        return build(NotificationKind.BOOKING_CONFIRMATION, d.userEmail(), subject, content, sendAt, d.bookingId(), event.eventId());
    }

    static Notification departureReminder(BookingConfirmedEvent event, Instant sendAt) {
        BookingConfirmedEvent.Data d = event.data();
        String subject = "Reminder - your flight " + d.flightNumber() + " departs soon";
        String content = """
                Hello,

                A reminder that your flight departs on %s.

                Booking:    #%d
                Flight:     %s
                Route:      %s -> %s
                Seats:      %d

                Please arrive at the airport in good time. Safe travels!
                """.formatted(WHEN.format(d.departureTime()), d.bookingId(), d.flightNumber(), d.origin(),
                d.destination(), d.noOfSeats());
        return build(NotificationKind.DEPARTURE_REMINDER, d.userEmail(), subject, content, sendAt, d.bookingId(), event.eventId());
    }

    static Notification cancellation(BookingCancelledEvent event, Instant sendAt) {
        BookingCancelledEvent.Data d = event.data();
        String subject = "Booking cancelled - " + d.flightNumber() + " from " + d.origin() + " to " + d.destination();
        String content = """
                Hello,

                Your booking #%d has been cancelled.

                Flight:     %s
                Route:      %s -> %s
                Departure:  %s
                Seats:      %d

                The seats have been released. We hope to see you on board another time.
                """.formatted(d.bookingId(), d.flightNumber(), d.origin(), d.destination(),
                WHEN.format(d.departureTime()), d.noOfSeats());
        return build(NotificationKind.BOOKING_CANCELLATION, d.userEmail(), subject, content, sendAt, d.bookingId(), event.eventId());
    }

    private static Notification build(NotificationKind kind, String email, String subject, String content,
                                      Instant sendAt, Long bookingId, String eventId) {
        Notification n = new Notification();
        n.setKind(kind);
        n.setRecipientEmail(email);
        n.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
        n.setContent(content);
        n.setNotificationTime(sendAt);
        n.setStatus(NotificationStatus.PENDING);
        n.setAttempts(0);
        n.setBookingId(bookingId);
        n.setSourceEventId(eventId);
        return n;
    }
}
