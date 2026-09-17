-- =====================================================================
-- Проверка целостности БД (п. 11.1 ТЗ): каждая команда ниже нарушает
-- одно ограничение схемы и должна завершиться ошибкой. Данные не меняются.
-- Запуск на загруженных данных:
--   psql -U postgres -d avia_booking -f sql/integrity_check.sql
-- =====================================================================
SET search_path TO avia;
\set ON_ERROR_STOP off
\echo '1. BR-03 uq_bookings_flight_seat_active: место 14C на рейсе 3 уже занято'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('TEST01', 1, 3, '14C', 'ECONOMY', 6400);
\echo '2. BR-04 uq_bookings_flight_passenger_active: у пассажира 3 уже есть активная бронь на рейс 3'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('TEST02', 3, 3, '20A', 'ECONOMY', 6400);
\echo '3. BR-05 fk_bookings_passenger: несуществующий пассажир'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('TEST03', 999, 3, '20A', 'ECONOMY', 6400);
\echo '4. BR-06 chk_bookings_price_positive: нулевая цена'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('TEST04', 2, 4, '20A', 'ECONOMY', 0);
\echo '5. chk_bookings_seat_format: место 12G'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('TEST05', 2, 4, '12G', 'ECONOMY', 6400);
\echo '6. chk_bookings_status: неизвестный статус'
UPDATE bookings SET status = 'LOST' WHERE id = 1;
\echo '7. uq_bookings_ref: повтор PNR'
INSERT INTO bookings (booking_ref, passenger_id, flight_id, seat_number, fare_class, price)
VALUES ('AB12CD', 2, 4, '20A', 'ECONOMY', 6400);
\echo '8. BR-10 fk ON DELETE RESTRICT: удаление пассажира с бронями'
DELETE FROM passengers WHERE id = 1;
\echo '9. chk_flights_route: аэропорты совпадают'
INSERT INTO flights (flight_number, airline, departure_airport, arrival_airport, departure_time,
                     arrival_time, aircraft_type, total_seats, base_price)
VALUES ('XX0001', 'Test', 'SVO', 'SVO', now(), now() + INTERVAL '1 hour', 'A320', 100, 1000);
\echo '10. chk_flights_time_order: прилёт раньше вылета'
INSERT INTO flights (flight_number, airline, departure_airport, arrival_airport, departure_time,
                     arrival_time, aircraft_type, total_seats, base_price)
VALUES ('XX0002', 'Test', 'SVO', 'LED', now(), now() - INTERVAL '1 hour', 'A320', 100, 1000);
\echo '11. chk_flights_seats: 700 мест'
INSERT INTO flights (flight_number, airline, departure_airport, arrival_airport, departure_time,
                     arrival_time, aircraft_type, total_seats, base_price)
VALUES ('XX0003', 'Test', 'SVO', 'LED', now(), now() + INTERVAL '1 hour', 'A320', 700, 1000);
\echo '12. uq_passengers_email и chk_passengers_email'
INSERT INTO passengers (last_name, first_name, birth_date, document_type, document_number, email, phone)
VALUES ('Тест', 'Тест', DATE '2000-01-01', 'PASSPORT_RF', '1111111111', 'ivanov@example.com', '+70000000000');
INSERT INTO passengers (last_name, first_name, birth_date, document_type, document_number, email, phone)
VALUES ('Тест', 'Тест', DATE '2000-01-01', 'PASSPORT_RF', '1111111111', 'not-an-email', '+70000000000');
\echo '13. chk_passengers_names: пустая фамилия'
INSERT INTO passengers (last_name, first_name, birth_date, document_type, document_number, email, phone)
VALUES ('  ', 'Тест', DATE '2000-01-01', 'PASSPORT_RF', '1111111111', 'test@example.com', '+70000000000');
\echo 'Проверка: число записей не изменилось'
SELECT (SELECT count(*) FROM passengers) AS passengers,
       (SELECT count(*) FROM flights)    AS flights,
       (SELECT count(*) FROM bookings)   AS bookings;
