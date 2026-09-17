-- =====================================================================
-- ИС «АВИА-БРОНЬ» v1.0 — начальная схема базы данных (приложение А ТЗ).
-- Миграцию применяет Flyway в схему avia при старте приложения.
-- Тот же DDL для ручного запуска через psql — console-app/sql/schema.sql;
-- scripts/verify.py проверяет, что оба файла описывают одинаковые объекты.
-- Применённую миграцию не редактируем: изменения — только новой версией V3, V4, ...
-- =====================================================================

-- ---------------------------------------------------------------------
-- Таблица 1. Пассажиры (участник предметной области)
-- ---------------------------------------------------------------------
CREATE TABLE passengers (
    id              BIGSERIAL,
    last_name       VARCHAR(60)  NOT NULL,
    first_name      VARCHAR(60)  NOT NULL,
    middle_name     VARCHAR(60),
    birth_date      DATE         NOT NULL,
    document_type   VARCHAR(24)  NOT NULL,
    document_number VARCHAR(20)  NOT NULL,
    email           VARCHAR(120) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_passengers            PRIMARY KEY (id),
    CONSTRAINT uq_passengers_email      UNIQUE (email),
    CONSTRAINT uq_passengers_document   UNIQUE (document_type, document_number),
    CONSTRAINT chk_passengers_names     CHECK (length(btrim(last_name)) > 0
                                           AND length(btrim(first_name)) > 0),
    CONSTRAINT chk_passengers_birth_date
        CHECK (birth_date BETWEEN DATE '1900-01-01' AND DATE '2100-01-01'),
    CONSTRAINT chk_passengers_email
        CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_passengers_doc_type  CHECK (document_type IN
        ('PASSPORT_RF', 'INTERNATIONAL_PASSPORT', 'BIRTH_CERTIFICATE', 'FOREIGN_DOCUMENT'))
);

COMMENT ON TABLE passengers IS 'Пассажиры — участники предметной области';

-- ---------------------------------------------------------------------
-- Таблица 2. Рейсы
-- ---------------------------------------------------------------------
CREATE TABLE flights (
    id                BIGSERIAL,
    flight_number     VARCHAR(8)    NOT NULL,
    airline           VARCHAR(60)   NOT NULL,
    departure_airport CHAR(3)       NOT NULL,
    arrival_airport   CHAR(3)       NOT NULL,
    departure_time    TIMESTAMP     NOT NULL,
    arrival_time      TIMESTAMP     NOT NULL,
    aircraft_type     VARCHAR(40)   NOT NULL,
    total_seats       INTEGER       NOT NULL,
    base_price        NUMERIC(10,2) NOT NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'SCHEDULED',

    CONSTRAINT pk_flights               PRIMARY KEY (id),
    CONSTRAINT uq_flights_number_date   UNIQUE (flight_number, departure_time),
    CONSTRAINT chk_flights_time_order   CHECK (arrival_time > departure_time),
    CONSTRAINT chk_flights_route        CHECK (departure_airport <> arrival_airport),
    CONSTRAINT chk_flights_iata         CHECK (departure_airport ~ '^[A-Z]{3}$'
                                           AND arrival_airport   ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_flights_seats        CHECK (total_seats BETWEEN 1 AND 600),
    CONSTRAINT chk_flights_price        CHECK (base_price > 0),
    CONSTRAINT chk_flights_status       CHECK (status IN
        ('SCHEDULED', 'DELAYED', 'DEPARTED', 'ARRIVED', 'CANCELLED'))
);

COMMENT ON TABLE flights IS 'Рейсы: маршрут, расписание, вместимость, базовый тариф';

-- ---------------------------------------------------------------------
-- Таблица 3. Бронирования (ОСНОВНАЯ СУЩНОСТЬ)
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id               BIGSERIAL,
    booking_ref      CHAR(6)       NOT NULL,
    passenger_id     BIGINT        NOT NULL,
    flight_id        BIGINT        NOT NULL,
    seat_number      VARCHAR(4)    NOT NULL,
    fare_class       VARCHAR(10)   NOT NULL,
    baggage_included BOOLEAN       NOT NULL DEFAULT FALSE,
    price            NUMERIC(10,2) NOT NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'CREATED',
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT pk_bookings             PRIMARY KEY (id),
    CONSTRAINT uq_bookings_ref         UNIQUE (booking_ref),

    CONSTRAINT fk_bookings_passenger   FOREIGN KEY (passenger_id)
        REFERENCES passengers (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_bookings_flight      FOREIGN KEY (flight_id)
        REFERENCES flights (id)    ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_bookings_ref_format  CHECK (booking_ref ~ '^[A-Z0-9]{6}$'),
    CONSTRAINT chk_bookings_seat_format CHECK (seat_number ~ '^[0-9]{1,2}[A-F]$'),
    CONSTRAINT chk_bookings_fare_class  CHECK (fare_class IN
        ('ECONOMY', 'COMFORT', 'BUSINESS')),
    CONSTRAINT chk_bookings_status      CHECK (status IN
        ('CREATED', 'PAID', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_bookings_price_positive CHECK (price > 0)
);

COMMENT ON TABLE bookings IS 'Бронирования — основная сущность системы';

-- ---------------------------------------------------------------------
-- Частичные уникальные индексы: реализация правил BR-03 и BR-04
-- Ограничение действует только для активных броней
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_bookings_flight_seat_active
    ON bookings (flight_id, seat_number)
    WHERE status NOT IN ('CANCELLED', 'EXPIRED');

CREATE UNIQUE INDEX uq_bookings_flight_passenger_active
    ON bookings (flight_id, passenger_id)
    WHERE status NOT IN ('CANCELLED', 'EXPIRED');

-- ---------------------------------------------------------------------
-- Индексы для поиска, фильтрации и сортировки (п. 8.4)
-- ---------------------------------------------------------------------
CREATE INDEX idx_bookings_status      ON bookings   (status);
CREATE INDEX idx_bookings_flight      ON bookings   (flight_id);
CREATE INDEX idx_bookings_passenger   ON bookings   (passenger_id);
CREATE INDEX idx_flights_departure    ON flights    (departure_time);
CREATE INDEX idx_flights_route        ON flights    (departure_airport, arrival_airport);
CREATE INDEX idx_passengers_last_name ON passengers (lower(last_name));

-- О проверке даты рождения. Ограничение chk_passengers_birth_date задаёт статический
-- допустимый диапазон: в CHECK не следует использовать изменяющиеся во времени выражения.
-- Правило «дата рождения не может быть в будущем» проверяется в слое service
-- (PassengerValidator) и приводит к ValidationException с кодом E-102.
