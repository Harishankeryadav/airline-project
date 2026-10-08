package com.airline.booking.entity;

/**
 * PENDING   - created, seats not yet confirmed
 * CONFIRMED - seats reserved on the flight
 * CANCELLED - cancelled by the user/admin, seats given back
 * FAILED    - could not be completed (e.g. not enough seats); nothing was reserved
 * (Node version: InProcess / Booked / Cancelled)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
