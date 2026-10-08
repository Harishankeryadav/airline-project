package com.airline.reminder.entity;

/**
 * PENDING   - waiting for its notification time
 * SENDING   - claimed by a dispatcher instance (reverted to PENDING if that instance dies)
 * SENT      - handed to the mail server
 * FAILED    - gave up after app.reminder.max-attempts (an admin can retry it)
 * CANCELLED - no longer needed (booking cancelled, or cancelled by an admin)
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}
