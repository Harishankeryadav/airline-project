CREATE TABLE cities (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cities_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE airports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(3)   NULL,
    address     VARCHAR(255) NULL,
    city_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_airports_code UNIQUE (code),
    -- RESTRICT (not CASCADE like the Node version): deleting a city must not silently wipe its airports
    CONSTRAINT fk_airports_city FOREIGN KEY (city_id) REFERENCES cities (id) ON DELETE RESTRICT,
    INDEX idx_airports_name (name),
    INDEX idx_airports_city (city_id)
) ENGINE = InnoDB;

CREATE TABLE airplanes (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    model_number  VARCHAR(100) NOT NULL,
    capacity      INT          NOT NULL DEFAULT 200,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_airplanes_capacity CHECK (capacity > 0)
) ENGINE = InnoDB;

CREATE TABLE flights (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    flight_number         VARCHAR(20)    NOT NULL,
    airplane_id           BIGINT         NOT NULL,
    departure_airport_id  BIGINT         NOT NULL,
    arrival_airport_id    BIGINT         NOT NULL,
    departure_time        DATETIME(6)    NOT NULL,
    arrival_time          DATETIME(6)    NOT NULL,
    price                 DECIMAL(10, 2) NOT NULL,
    boarding_gate         VARCHAR(20)    NULL,
    total_seats           INT            NOT NULL,
    available_seats       INT            NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_flights_number UNIQUE (flight_number),
    CONSTRAINT fk_flights_airplane  FOREIGN KEY (airplane_id)          REFERENCES airplanes (id),
    CONSTRAINT fk_flights_departure FOREIGN KEY (departure_airport_id) REFERENCES airports (id),
    CONSTRAINT fk_flights_arrival   FOREIGN KEY (arrival_airport_id)   REFERENCES airports (id),
    -- database-level safety nets: seats can never go negative or exceed capacity
    CONSTRAINT ck_flights_seats CHECK (available_seats >= 0 AND available_seats <= total_seats),
    CONSTRAINT ck_flights_times CHECK (arrival_time > departure_time),
    CONSTRAINT ck_flights_airports CHECK (departure_airport_id <> arrival_airport_id),
    INDEX idx_flights_route (departure_airport_id, arrival_airport_id, departure_time),
    INDEX idx_flights_price (price)
) ENGINE = InnoDB;
