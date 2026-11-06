-- V1: схема и демо-данные КР1 (консольное приложение / JavaFX) — основа серверной базы

-- =====================================================================
-- ИС «АВИАБРОНИРОВАНИЕ» — схема базы данных
-- Контрольная работа № 1. Вариант: пассажирские авиаперевозки
-- СУБД: PostgreSQL 16
-- Запуск: psql -U postgres -d avia_booking -f sql/schema.sql
-- =====================================================================


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
        ('CREATED', 'PAID', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
);

-- =====================================================================
-- ИС «АВИАБРОНИРОВАНИЕ» — начальные тестовые данные
-- 5 пассажиров, 5 рейсов, 10 бронирований в трёх+ статусах.
-- Даты рейсов заданы относительно CURRENT_DATE, чтобы не оказаться
-- в прошлом к моменту демонстрации.
-- Запуск: psql -U postgres -d avia_booking -f sql/data.sql
-- =====================================================================

INSERT INTO passengers
    (last_name, first_name, middle_name, birth_date,
     document_type, document_number, email, phone) VALUES
('Иванов',    'Иван',  'Иванович',   DATE '1985-04-12',
 'PASSPORT_RF',            '4510123456',     'ivanov@mail.ru',     '+79161001122'),
('Петрова',   'Анна',  'Сергеевна',  DATE '1992-11-03',
 'PASSPORT_RF',            '4512654321',     'petrova@gmail.com',    '+79162003344'),
('Сидоров',   'Пётр',  'Николаевич', DATE '1988-03-14',
 'PASSPORT_RF',            '4512889967',     'sidorov@yandex.ru',    '+79163005566'),
('Кузнецова', 'Мария', 'Олеговна',   DATE '1979-07-22',
 'INTERNATIONAL_PASSPORT', '750123456',      'kuznetsova@mail.ru', '+79164007788'),
('Соколов',   'Артём', 'Дмитриевич', DATE '2016-09-30',
 'BIRTH_CERTIFICATE',      'VIII-MU-123456', 'sokolov@gmail.com',    '+79165009900');

INSERT INTO flights
    (flight_number, airline, departure_airport, arrival_airport,
     departure_time, arrival_time, aircraft_type, total_seats, base_price, status) VALUES
('SU1416', 'Аэрофлот',            'SVO', 'LED',
 CURRENT_DATE - 8  + TIME '08:40', CURRENT_DATE - 8  + TIME '10:00', 'Airbus A320',    180, 6400.00, 'ARRIVED'),
('SU1420', 'Аэрофлот',            'SVO', 'LED',
 CURRENT_DATE + 1  + TIME '08:40', CURRENT_DATE + 1  + TIME '10:00', 'Airbus A320',    180, 6400.00, 'SCHEDULED'),
('SU1421', 'Аэрофлот',            'LED', 'SVO',
 CURRENT_DATE + 1  + TIME '19:20', CURRENT_DATE + 1  + TIME '20:40', 'Airbus A320',    180, 6400.00, 'SCHEDULED'),
('U62810', 'Уральские авиалинии', 'SVO', 'KZN',
 CURRENT_DATE + 13 + TIME '06:50', CURRENT_DATE + 13 + TIME '08:25', 'Airbus A321',    220, 4900.00, 'DELAYED'),
('SU1130', 'Аэрофлот',            'SVO', 'AER',
 CURRENT_DATE + 17 + TIME '09:00', CURRENT_DATE + 17 + TIME '11:30', 'Boeing 737-800', 128, 9200.00, 'CANCELLED');

INSERT INTO bookings
    (passenger_id, flight_id, seat_number, fare_class, price, status) VALUES
(1, 1, '12A', 'ECONOMY',  6400.00,  'COMPLETED'),
(2, 1, '12B', 'COMFORT', 10240.00,  'COMPLETED'),
(3, 2, '3A',  'BUSINESS', 16000.00, 'CHECKED_IN'),
(4, 2, '14C', 'COMFORT',  10240.00, 'CHECKED_IN'),
(1, 3, '5F',  'ECONOMY',   8900.00, 'PAID'),
(5, 3, '21D', 'ECONOMY',   6400.00, 'PAID'),
(2, 4, '7A',  'ECONOMY',   4900.00, 'PAID'),
(3, 4, '7B',  'COMFORT',   7840.00, 'CREATED'),
(4, 2, '30E', 'ECONOMY',   6400.00, 'CANCELLED'),
(5, 4, '12F', 'ECONOMY',   4900.00, 'CANCELLED');
