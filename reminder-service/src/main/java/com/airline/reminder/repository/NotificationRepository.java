package com.airline.reminder.repository;

import com.airline.reminder.entity.Notification;
import com.airline.reminder.entity.NotificationKind;
import com.airline.reminder.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventIdAndKind(String sourceEventId, NotificationKind kind);

    boolean existsByBookingIdAndKind(Long bookingId, NotificationKind kind);

    List<Notification> findTop200ByOrderByIdDesc();

    List<Notification> findTop200ByStatusOrderByIdDesc(NotificationStatus status);

    /** Notifications that are due. Index: (status, notification_time). */
    List<Notification> findTop50ByStatusAndNotificationTimeLessThanEqualOrderByNotificationTimeAsc(
            NotificationStatus status, Instant now);

    /**
     * Claim = compare-and-set PENDING -> SENDING (also counts the attempt). Returns 1 only for the caller that won,
     * so several reminder-service instances never send the same email twice.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :to, n.attempts = n.attempts + 1, n.updatedAt = :now "
            + "where n.id = :id and n.status = :from")
    int claim(@Param("id") Long id, @Param("from") NotificationStatus from, @Param("to") NotificationStatus to,
              @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :status, n.sentAt = :now, n.lastError = null, n.updatedAt = :now "
            + "where n.id = :id")
    int markSent(@Param("id") Long id, @Param("status") NotificationStatus status, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :status, n.lastError = :error, n.updatedAt = :now where n.id = :id")
    int markFailed(@Param("id") Long id, @Param("status") NotificationStatus status, @Param("error") String error,
                   @Param("now") Instant now);

    /** Put back to PENDING for a later attempt. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :status, n.notificationTime = :next, n.lastError = :error, "
            + "n.updatedAt = :now where n.id = :id")
    int reschedule(@Param("id") Long id, @Param("status") NotificationStatus status, @Param("next") Instant next,
                   @Param("error") String error, @Param("now") Instant now);

    /** SENDING rows untouched for a long time belong to an instance that died: make them PENDING again. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :pending, n.updatedAt = :now "
            + "where n.status = :sending and n.updatedAt < :cutoff")
    int requeueStale(@Param("pending") NotificationStatus pending, @Param("sending") NotificationStatus sending,
                     @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    /** Cancels the not-yet-sent emails of a booking (used when the booking is cancelled). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :cancelled, n.updatedAt = :now "
            + "where n.bookingId = :bookingId and n.status = :pending and n.kind in (:kinds)")
    int cancelPendingForBooking(@Param("bookingId") Long bookingId, @Param("pending") NotificationStatus pending,
                                @Param("cancelled") NotificationStatus cancelled,
                                @Param("kinds") Collection<NotificationKind> kinds, @Param("now") Instant now);

    /** Admin: change a notification's status only if it currently has the expected status. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = :to, n.attempts = :attempts, n.notificationTime = :time, "
            + "n.lastError = null, n.updatedAt = :now where n.id = :id and n.status = :from")
    int transition(@Param("id") Long id, @Param("from") NotificationStatus from, @Param("to") NotificationStatus to,
                   @Param("attempts") int attempts, @Param("time") Instant time, @Param("now") Instant now);
}
