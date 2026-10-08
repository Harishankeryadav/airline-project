CREATE TABLE notifications (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    kind              VARCHAR(30)  NOT NULL,
    recipient_email   VARCHAR(254) NOT NULL,
    subject           VARCHAR(200) NOT NULL,
    content           TEXT         NOT NULL,
    notification_time DATETIME(6)  NOT NULL,   -- when it becomes due (the Node version stored this as a STRING)
    status            VARCHAR(20)  NOT NULL,
    attempts          INT          NOT NULL DEFAULT 0,
    last_error        VARCHAR(255) NULL,
    sent_at           DATETIME(6)  NULL,
    booking_id        BIGINT       NULL,       -- from booking-service (no cross-database FK)
    source_event_id   VARCHAR(100) NULL,       -- id of the RabbitMQ event that caused it
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- A redelivered event can never create a second email (NULLs are allowed many times: admin-created ones).
    CONSTRAINT uk_notifications_event_kind UNIQUE (source_event_id, kind),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notifications_kind CHECK (kind IN ('BOOKING_CONFIRMATION', 'DEPARTURE_REMINDER', 'BOOKING_CANCELLATION', 'CUSTOM')),
    INDEX idx_notifications_due (status, notification_time),
    INDEX idx_notifications_booking (booking_id, status)
) ENGINE = InnoDB;
