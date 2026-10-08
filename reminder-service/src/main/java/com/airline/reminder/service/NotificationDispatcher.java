package com.airline.reminder.service;

import com.airline.reminder.config.ReminderProperties;
import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.mail.EmailSender;
import com.airline.reminder.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends due notifications. (In the Node version the cron job was written but never started, so no reminder was ever
 * sent, and failures were swallowed.)
 *
 * Safe with several instances: each notification is CLAIMED first (PENDING -> SENDING, compare-and-set), and only the
 * instance that wins the claim sends it. A failed send is retried with a growing delay, then marked FAILED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private static final Duration STALE_SENDING_AFTER = Duration.ofMinutes(10);

    private final NotificationRepository repository;
    private final EmailSender emailSender;
    private final ReminderProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.dispatch.interval-ms:10000}")
    public void dispatchDue() {
        Instant now = clock.instant();
        int recovered = repository.requeueStale(NotificationStatus.PENDING, NotificationStatus.SENDING,
                now.minus(STALE_SENDING_AFTER), now);
        if (recovered > 0) {
            log.warn("{} notification(s) were stuck in SENDING and have been re-queued", recovered);
        }
        for (Notification notification : repository
                .findTop50ByStatusAndNotificationTimeLessThanEqualOrderByNotificationTimeAsc(NotificationStatus.PENDING, now)) {
            dispatch(notification);
        }
    }

    void dispatch(Notification notification) {
        if (repository.claim(notification.getId(), NotificationStatus.PENDING, NotificationStatus.SENDING, clock.instant()) == 0) {
            return; // another instance (or an admin cancel) got there first
        }
        int attempt = notification.getAttempts() + 1;   // the claim just counted this attempt in the database
        try {
            emailSender.send(notification.getRecipientEmail(), notification.getSubject(), notification.getContent());
            repository.markSent(notification.getId(), NotificationStatus.SENT, clock.instant());
            log.info("Sent {} #{} to {}", notification.getKind(), notification.getId(), notification.getRecipientEmail());
        } catch (RuntimeException e) {
            String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage());
            Instant now = clock.instant();
            if (attempt >= properties.maxAttempts()) {
                repository.markFailed(notification.getId(), NotificationStatus.FAILED, error, now);
                log.error("Giving up on {} #{} after {} attempts: {}", notification.getKind(), notification.getId(), attempt, error);
            } else {
                Instant next = now.plus(Duration.ofMinutes(properties.retryBackoffMinutes() * attempt));
                repository.reschedule(notification.getId(), NotificationStatus.PENDING, next, error, now);
                log.warn("Sending {} #{} failed (attempt {}), will retry at {}: {}", notification.getKind(),
                        notification.getId(), attempt, next, error);
            }
        }
    }

    private static String truncate(String text) {
        return text.length() <= 255 ? text : text.substring(0, 255);
    }
}
