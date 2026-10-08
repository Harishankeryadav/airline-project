package com.airline.booking.service;

import com.airline.booking.entity.Booking;
import com.airline.booking.entity.BookingStatus;
import com.airline.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Finishes work that could not be completed inline because a dependency was down:
 *  - CONFIRMED bookings whose BOOKING_CONFIRMED event never reached RabbitMQ  -> publish it
 *  - CANCELLED bookings whose seats were not given back to flights-service    -> release them
 * Only rows older than a short grace period are touched, so it never races with the request that just created them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRecoveryJob {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    @Value("${app.recovery.grace-seconds:30}")
    private long graceSeconds;

    @Scheduled(fixedDelayString = "${app.recovery.interval-ms:60000}")
    public void recover() {
        Instant cutoff = clock.instant().minusSeconds(graceSeconds);

        for (Booking booking : bookingRepository
                .findTop50ByStatusAndNotificationPublishedFalseAndUpdatedAtBeforeOrderByIdAsc(BookingStatus.CONFIRMED, cutoff)) {
            if (!bookingService.publishConfirmedEvent(booking)) {
                break; // broker still down - no point trying the rest now
            }
            log.info("Re-published BOOKING_CONFIRMED for booking {}", booking.getId());
        }

        for (Booking booking : bookingRepository
                .findTop50ByStatusAndSeatReleasePendingTrueAndUpdatedAtBeforeOrderByIdAsc(BookingStatus.CANCELLED, cutoff)) {
            bookingService.tryReleaseSeats(booking);
        }
    }
}
