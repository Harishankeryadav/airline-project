package com.airline.reminder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.reminder.* - leadHours before departure, maxAttempts per email, retryBackoffMinutes (x attempt number). */
@ConfigurationProperties(prefix = "app.reminder")
public record ReminderProperties(long leadHours, int maxAttempts, long retryBackoffMinutes) {
}
