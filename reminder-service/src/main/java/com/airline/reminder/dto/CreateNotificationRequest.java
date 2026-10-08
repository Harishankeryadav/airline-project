package com.airline.reminder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Same fields as the Node "ticket" (subject, content, recipientEmail, notificationTime); notificationTime is optional. */
public record CreateNotificationRequest(
        @NotBlank(message = "subject is required")
        @Size(max = 200, message = "subject must be at most 200 characters")
        String subject,

        @NotBlank(message = "content is required")
        @Size(max = 10000, message = "content must be at most 10000 characters")
        String content,

        @NotBlank(message = "recipientEmail is required")
        @Email(message = "recipientEmail must be a valid email address")
        @Size(max = 254, message = "recipientEmail must be at most 254 characters")
        String recipientEmail,

        /** ISO-8601, e.g. 2026-11-01T09:30:00Z. Omit to send as soon as possible. */
        Instant notificationTime) {
}
