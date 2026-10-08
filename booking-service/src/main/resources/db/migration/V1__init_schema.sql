CREATE TABLE bookings (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT         NOT NULL,   -- id from auth-service (no cross-database FK)
    user_email              VARCHAR(254)   NOT NULL,   -- from the JWT; used for the confirmation email
    flight_id               BIGINT         NOT NULL,   -- id from flights-service (no cross-database FK)
    -- snapshot of the flight at booking time, so "my bookings" works even if flights-service is down
    flight_number           VARCHAR(20)    NOT NULL,
    origin                  VARCHAR(200)   NOT NULL,
    destination             VARCHAR(200)   NOT NULL,
    departure_time          DATETIME(6)    NOT NULL,
    no_of_seats             INT            NOT NULL,
    unit_price              DECIMAL(10, 2) NOT NULL,
    total_cost              DECIMAL(12, 2) NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    failure_reason          VARCHAR(255)   NULL,
    -- recovery flags (see BookingRecoveryJob)
    notification_published  BOOLEAN        NOT NULL DEFAULT FALSE,
    seat_release_pending    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_bookings_seats  CHECK (no_of_seats > 0),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    INDEX idx_bookings_user (user_id, created_at),
    INDEX idx_bookings_flight (flight_id),
    INDEX idx_bookings_status (status, notification_published, seat_release_pending)
) ENGINE = InnoDB;
