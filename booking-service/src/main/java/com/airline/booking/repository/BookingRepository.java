package com.airline.booking.repository;

import com.airline.booking.entity.Booking;
import com.airline.booking.entity.BookingStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByStatus(BookingStatus status, Sort sort);

    /** Confirmed bookings whose event never reached RabbitMQ (older than the grace period). */
    List<Booking> findTop50ByStatusAndNotificationPublishedFalseAndUpdatedAtBeforeOrderByIdAsc(
            BookingStatus status, Instant cutoff);

    /** Cancelled bookings whose seats have not been given back yet (older than the grace period). */
    List<Booking> findTop50ByStatusAndSeatReleasePendingTrueAndUpdatedAtBeforeOrderByIdAsc(
            BookingStatus status, Instant cutoff);

    /**
     * Compare-and-set status change: succeeds (returns 1) only if the booking is still in {@code from}.
     * Makes "cancel" safe against double clicks and concurrent requests.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking b set b.status = :to, b.seatReleasePending = :pending, b.updatedAt = :now "
            + "where b.id = :id and b.status = :from")
    int transition(@Param("id") Long id, @Param("from") BookingStatus from, @Param("to") BookingStatus to,
                   @Param("pending") boolean pending, @Param("now") Instant now);

    /**
     * Compare-and-set on the release flag. {@code expected=true, pending=false} CLAIMS the release job, so two
     * instances never give the same seats back twice; {@code expected=false, pending=true} puts it back after a failure.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking b set b.seatReleasePending = :pending, b.updatedAt = :now "
            + "where b.id = :id and b.seatReleasePending = :expected")
    int setSeatReleasePending(@Param("id") Long id, @Param("expected") boolean expected,
                              @Param("pending") boolean pending, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking b set b.notificationPublished = true where b.id = :id")
    int markNotificationPublished(@Param("id") Long id);
}
