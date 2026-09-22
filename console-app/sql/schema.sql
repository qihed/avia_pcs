-- =====================================================================
-- ИС «АВИАБРОНИРОВАНИЕ» — схема базы данных
-- Контрольная работа № 1. Вариант: пассажирские авиаперевозки
-- СУБД: PostgreSQL 16
-- Запуск: psql -U postgres -d avia_booking -f sql/schema.sql
-- =====================================================================

DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS flights;
DROP TABLE IF EXISTS passengers;

CREATE TABLE passengers (
    id              BIGSERIAL PRIMARY KEY,
    last_name       VARCHAR(60)  NOT NULL,
    first_name      VARCHAR(60)  NOT NULL,
    middle_name     VARCHAR(60),
    birth_date      DATE         NOT NULL,
    document_type   VARCHAR(24)  NOT NULL,
    document_number VARCHAR(20)  NOT NULL,
    email           VARCHAR(120) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_passengers_doc_type CHECK (document_type IN
        ('PASSPORT_RF', 'INTERNATIONAL_PASSPORT', 'BIRTH_CERTIFICATE', 'FOREIGN_DOCUMENT'))
);

CREATE TABLE flights (
    id                BIGSERIAL PRIMARY KEY,
    flight_number     VARCHAR(8)    NOT NULL,
    airline           VARCHAR(60)   NOT NULL,
    departure_airport CHAR(3)       NOT NULL,
    arrival_airport   CHAR(3)       NOT NULL,
    departure_time    TIMESTAMP     NOT NULL,
    arrival_time      TIMESTAMP     NOT NULL,
    aircraft_type     VARCHAR(40)   NOT NULL,
    total_seats       INTEGER       NOT NULL CHECK (total_seats > 0),
    base_price        NUMERIC(10,2) NOT NULL CHECK (base_price > 0),
    status            VARCHAR(16)   NOT NULL DEFAULT 'SCHEDULED',

    CONSTRAINT chk_flights_route  CHECK (departure_airport <> arrival_airport),
    CONSTRAINT chk_flights_status CHECK (status IN
        ('SCHEDULED', 'DELAYED', 'DEPARTED', 'ARRIVED', 'CANCELLED'))
);

CREATE TABLE bookings (
    id           BIGSERIAL PRIMARY KEY,
    passenger_id BIGINT        NOT NULL REFERENCES passengers(id),
    flight_id    BIGINT        NOT NULL REFERENCES flights(id),
    seat_number  VARCHAR(4)    NOT NULL,
    fare_class   VARCHAR(10)   NOT NULL,
    price        NUMERIC(10,2) NOT NULL CHECK (price > 0),
    status       VARCHAR(12)   NOT NULL DEFAULT 'CREATED',
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_bookings_seat_format CHECK (seat_number ~ '^[0-9]{1,2}[A-F]$'),
    CONSTRAINT chk_bookings_fare_class  CHECK (fare_class IN ('ECONOMY', 'COMFORT', 'BUSINESS')),
    CONSTRAINT chk_bookings_status      CHECK (status IN
        ('CREATED', 'PAID', 'CHECKED_IN', 'COMPLETED', 'CANCELLED'))
);
