package com.airline.reminder.entity;

public enum NotificationKind {
    BOOKING_CONFIRMATION,   // sent right away when a booking is confirmed
    DEPARTURE_REMINDER,     // sent app.reminder.lead-hours before departure
    BOOKING_CANCELLATION,   // sent when a booking is cancelled
    CUSTOM                  // created by an admin through POST /api/v1/tickets
}
