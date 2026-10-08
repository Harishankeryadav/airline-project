package com.airline.reminder.service;

import com.airline.reminder.config.ReminderProperties;
import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.mail.EmailSender;
import com.airline.reminder.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Mock
    private NotificationRepository repository;
    @Mock
    private EmailSender emailSender;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(repository, emailSender, new ReminderProperties(24, 5, 5),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Notification notification(int attemptsBeforeClaim) {
        Notification n = new Notification();
        n.setId(1L);
        n.setKind(NotificationKind.BOOKING_CONFIRMATION);
        n.setRecipientEmail("ana@example.com");
        n.setSubject("Booking confirmed");
        n.setContent("Hello");
        n.setStatus(NotificationStatus.PENDING);
        n.setAttempts(attemptsBeforeClaim);
        return n;
    }

    private void claimIsWon() {
        when(repository.claim(eq(1L), eq(NotificationStatus.PENDING), eq(NotificationStatus.SENDING), any(Instant.class))).thenReturn(1);
    }

    @Test
    void sendsAndMarksSentWhenTheClaimIsWon() {
        claimIsWon();

        dispatcher.dispatch(notification(0));

        verify(emailSender).send("ana@example.com", "Booking confirmed", "Hello");
        verify(repository).markSent(eq(1L), eq(NotificationStatus.SENT), any(Instant.class));
    }

    @Test
    void doesNotSendWhenAnotherInstanceAlreadyClaimedIt() {
        when(repository.claim(eq(1L), eq(NotificationStatus.PENDING), eq(NotificationStatus.SENDING), any(Instant.class))).thenReturn(0);

        dispatcher.dispatch(notification(0));

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(repository, never()).markSent(any(), any(), any());
    }

    @Test
    void aFailedSendIsRescheduledWithBackoff() {
        claimIsWon();
        doThrow(new MailSendException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        dispatcher.dispatch(notification(0));   // this is attempt 1 of 5 -> retry in 1 x 5 minutes

        verify(repository).reschedule(eq(1L), eq(NotificationStatus.PENDING), eq(NOW.plus(Duration.ofMinutes(5))),
                anyString(), any(Instant.class));
        verify(repository, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void theLastFailedAttemptMarksTheNotificationFailed() {
        claimIsWon();
        doThrow(new MailSendException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        dispatcher.dispatch(notification(4));   // 4 earlier attempts + this one = 5 = max

        verify(repository).markFailed(eq(1L), eq(NotificationStatus.FAILED), anyString(), any(Instant.class));
        verify(repository, never()).reschedule(any(), any(), any(), any(), any());
    }
}
