package com.airline.reminder.service;

import com.airline.reminder.config.ReminderProperties;
import com.airline.reminder.dto.CreateNotificationRequest;
import com.airline.reminder.dto.NotificationResponse;
import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.event.BookingCancelledEvent;
import com.airline.reminder.event.BookingConfirmedEvent;
import com.airline.reminder.exception.ConflictException;
import com.airline.reminder.exception.ResourceNotFoundException;
import com.airline.reminder.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final ReminderProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ events from booking-service

    /**
     * Creates the confirmation email (due now) and the departure reminder (due lead-hours before departure).
     * Idempotent: a redelivered event is recognised by (eventId, kind) and creates nothing.
     * Throws on malformed events - RabbitMQ then retries and finally parks them in the dead-letter queue.
     */
    @Transactional
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        BookingConfirmedEvent.Data data = requireData(event == null ? null : event.data(), event == null ? null : event.eventId());
        if (repository.existsByBookingIdAndKind(data.bookingId(), NotificationKind.BOOKING_CANCELLATION)) {
            // The cancellation event overtook the confirmation event (they travel on different queues).
            log.info("Ignoring {} - booking {} was already cancelled", event.eventId(), data.bookingId());
            return;
        }
        Instant now = clock.instant();
        saveIfNew(NotificationFactory.confirmation(event, now));

        Instant remindAt = data.departureTime().minus(Duration.ofHours(properties.leadHours()));
        if (remindAt.isAfter(now)) {   // booked less than lead-hours before departure: the confirmation is enough
            saveIfNew(NotificationFactory.departureReminder(event, remindAt));
        }
    }

    /** Drops the booking's unsent emails and sends a cancellation notice. */
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        BookingCancelledEvent.Data data = requireData(event == null ? null : event.data(), event == null ? null : event.eventId());
        Instant now = clock.instant();
        int dropped = repository.cancelPendingForBooking(data.bookingId(), NotificationStatus.PENDING,
                NotificationStatus.CANCELLED, List.of(NotificationKind.BOOKING_CONFIRMATION, NotificationKind.DEPARTURE_REMINDER), now);
        log.info("Booking {} cancelled: {} pending email(s) dropped", data.bookingId(), dropped);
        saveIfNew(NotificationFactory.cancellation(event, now));
    }

    // ------------------------------------------------------------------ admin API ("tickets")

    @Transactional
    public NotificationResponse createCustom(CreateNotificationRequest request) {
        Notification n = new Notification();
        n.setKind(NotificationKind.CUSTOM);
        n.setRecipientEmail(request.recipientEmail().trim());
        n.setSubject(request.subject().trim());
        n.setContent(request.content());
        n.setNotificationTime(request.notificationTime() != null ? request.notificationTime() : clock.instant());
        n.setStatus(NotificationStatus.PENDING);
        return toResponse(repository.save(n));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(NotificationStatus status) {
        List<Notification> rows = status == null ? repository.findTop200ByOrderByIdDesc()
                : repository.findTop200ByStatusOrderByIdDesc(status);
        return rows.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(Long id) {
        return toResponse(find(id));
    }

    /** Stops a PENDING notification from being sent. */
    @Transactional
    public NotificationResponse cancel(Long id) {
        Notification n = find(id);
        Instant now = clock.instant();
        if (repository.transition(id, NotificationStatus.PENDING, NotificationStatus.CANCELLED, n.getAttempts(), n.getNotificationTime(), now) == 0) {
            throw new ConflictException("Only PENDING notifications can be cancelled (this one is " + currentStatus(id) + ")");
        }
        return toResponse(find(id));
    }

    /** Re-queues a FAILED notification for sending right away. */
    @Transactional
    public NotificationResponse retry(Long id) {
        find(id);
        Instant now = clock.instant();
        if (repository.transition(id, NotificationStatus.FAILED, NotificationStatus.PENDING, 0, now, now) == 0) {
            throw new ConflictException("Only FAILED notifications can be retried (this one is " + currentStatus(id) + ")");
        }
        return toResponse(find(id));
    }

    // ------------------------------------------------------------------ helpers

    private void saveIfNew(Notification notification) {
        if (repository.existsBySourceEventIdAndKind(notification.getSourceEventId(), notification.getKind())) {
            log.info("Duplicate event {} ({}) ignored", notification.getSourceEventId(), notification.getKind());
            return;
        }
        repository.save(notification);
    }

    private static <T> T requireData(T data, String eventId) {
        if (data == null) {
            throw new IllegalArgumentException("Event " + eventId + " has no data");
        }
        return data;
    }

    private Notification find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found with id " + id));
    }

    private NotificationStatus currentStatus(Long id) {
        return repository.findById(id).map(Notification::getStatus).orElse(null);
    }

    static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getKind(), n.getRecipientEmail(), n.getSubject(), n.getStatus(),
                n.getAttempts(), n.getLastError(), n.getNotificationTime(), n.getSentAt(), n.getBookingId(), n.getCreatedAt());
    }
}
