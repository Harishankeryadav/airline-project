package com.airline.reminder.dto;

import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationKind kind,
        String recipientEmail,
        String subject,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant notificationTime,
        Instant sentAt,
        Long bookingId,
        Instant createdAt) {
}
