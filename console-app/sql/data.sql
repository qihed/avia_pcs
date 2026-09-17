-- =====================================================================
-- ИС «АВИА-БРОНЬ» v1.0 — начальные тестовые данные (приложение Б ТЗ)
-- 6 пассажиров, 8 рейсов, 14 бронирований во всех шести статусах.
-- Стоимости рассчитаны по правилу BR-06:
--     price = base_price * k(fare_class) + baggage_fee
--
-- Даты рейсов задаются ОТНОСИТЕЛЬНО ДНЯ ЗАГРУЗКИ (CURRENT_DATE), а не
-- фиксированными значениями из ТЗ (28.08.2026 ... 22.09.2026). Иначе к дню
-- защиты все рейсы окажутся в прошлом и правило BR-01 закроет их для продажи,
-- а сценарии TC-04 ... TC-10 станет невозможно показать. Порядок рейсов,
-- интервалы между ними, время вылета и статусы совпадают с ТЗ.
--
-- Три брони в статусе CREATED создаются «несколько минут назад»: через
-- 30 минут после загрузки правило BR-09 переведёт их в EXPIRED. Перед
-- демонстрацией скрипты schema.sql и data.sql нужно выполнить заново.
-- =====================================================================

SET search_path TO avia;
SET TIME ZONE 'Europe/Moscow';   -- CON-06: все времена в UTC+3

-- ---------------------------------------------------------------------
-- Пассажиры (6)
-- ---------------------------------------------------------------------
INSERT INTO passengers
    (last_name, first_name, middle_name, birth_date,
     document_type, document_number, email, phone, created_at) VALUES
('Иванов',    'Иван',  'Иванович',   DATE '1985-04-12',
 'PASSPORT_RF',            '4510123456',     'ivanov@example.com',     '+79161001122', now() - INTERVAL '40 days'),
('Петрова',   'Анна',  'Сергеевна',  DATE '1992-11-03',
 'PASSPORT_RF',            '4512654321',     'petrova@example.com',    '+79162003344', now() - INTERVAL '38 days'),
('Сидоров',   'Пётр',  'Николаевич', DATE '1988-03-14',
 'PASSPORT_RF',            '4512889967',     'sidorov@example.com',    '+79163005566', now() - INTERVAL '30 days'),
('Кузнецова', 'Мария', 'Олеговна',   DATE '1979-07-22',
 'INTERNATIONAL_PASSPORT', '750123456',      'kuznetsova@example.com', '+79164007788', now() - INTERVAL '25 days'),
('Соколов',   'Артём', 'Дмитриевич', DATE '2016-09-30',
 'BIRTH_CERTIFICATE',      'VIII-MU-123456', 'sokolov@example.com',    '+79165009900', now() - INTERVAL '20 days'),
('Новиков',   'Денис', 'Валерьевич', DATE '1995-01-19',
 'FOREIGN_DOCUMENT',       'C01X00T47',      'novikov@example.com',    '+79166001133', now() - INTERVAL '10 days');

-- ---------------------------------------------------------------------
-- Рейсы (8): два выполненных, четыре по расписанию, один задержан,
--            один отменён — для демонстрации правила BR-01
-- ---------------------------------------------------------------------
INSERT INTO flights
    (flight_number, airline, departure_airport, arrival_airport,
     departure_time, arrival_time, aircraft_type, total_seats, base_price, status) VALUES
('SU1416', 'Аэрофлот',            'SVO', 'LED',
 CURRENT_DATE - 8  + TIME '08:40', CURRENT_DATE - 8  + TIME '10:00', 'Airbus A320',    180, 6400.00, 'ARRIVED'),
('SU1102', 'Аэрофлот',            'SVO', 'AER',
 CURRENT_DATE - 6  + TIME '19:05', CURRENT_DATE - 6  + TIME '21:35', 'Boeing 737-800', 128, 8300.00, 'ARRIVED'),
('SU1420', 'Аэрофлот',            'SVO', 'LED',
 CURRENT_DATE + 1  + TIME '08:40', CURRENT_DATE + 1  + TIME '10:00', 'Airbus A320',    180, 6400.00, 'SCHEDULED'),
('SU1421', 'Аэрофлот',            'LED', 'SVO',
 CURRENT_DATE + 1  + TIME '19:20', CURRENT_DATE + 1  + TIME '20:40', 'Airbus A320',    180, 6400.00, 'SCHEDULED'),
('SU1108', 'Аэрофлот',            'SVO', 'AER',
 CURRENT_DATE + 7  + TIME '19:05', CURRENT_DATE + 7  + TIME '21:35', 'Boeing 737-800', 128, 8300.00, 'SCHEDULED'),
('FV6021', 'Россия',              'LED', 'KZN',
 CURRENT_DATE + 11 + TIME '11:30', CURRENT_DATE + 11 + TIME '13:40', 'Airbus A319',    156, 5200.00, 'SCHEDULED'),
('U62810', 'Уральские авиалинии', 'SVO', 'KZN',
 CURRENT_DATE + 13 + TIME '06:50', CURRENT_DATE + 13 + TIME '08:25', 'Airbus A321',    220, 4900.00, 'DELAYED'),
('SU1130', 'Аэрофлот',            'SVO', 'AER',
 CURRENT_DATE + 17 + TIME '09:00', CURRENT_DATE + 17 + TIME '11:30', 'Boeing 737-800', 128, 9200.00, 'CANCELLED');

-- ---------------------------------------------------------------------
-- Бронирования (14): все шесть статусов, три класса обслуживания
-- price = base_price * k(fare_class) + baggage_fee    (BR-06)
-- ---------------------------------------------------------------------
INSERT INTO bookings
    (booking_ref, passenger_id, flight_id, seat_number,
     fare_class, baggage_included, price, status, created_at, updated_at) VALUES
('AB12CD', 1, 1, '12A', 'ECONOMY',  FALSE,  6400.00, 'COMPLETED',
 now() - INTERVAL '15 days', now() - INTERVAL '8 days'),
('NR61WS', 2, 2, '12B', 'COMFORT',  TRUE,  15780.00, 'COMPLETED',
 now() - INTERVAL '12 days', now() - INTERVAL '6 days'),
('TY19GH', 3, 3, '3A',  'BUSINESS', TRUE,  16000.00, 'CHECKED_IN',
 now() - INTERVAL '5 days',  now() - INTERVAL '2 hours'),
('ZX45TY', 4, 3, '14C', 'COMFORT',  TRUE,  12740.00, 'CHECKED_IN',
 now() - INTERVAL '4 days',  now() - INTERVAL '1 hour'),
('LM77PQ', 1, 4, '5F',  'ECONOMY',  TRUE,   8900.00, 'PAID',
 now() - INTERVAL '3 days',  now() - INTERVAL '3 days'),
('RT58NB', 5, 5, '21D', 'ECONOMY',  FALSE,  8300.00, 'PAID',
 now() - INTERVAL '3 days',  now() - INTERVAL '3 days'),
('HG23KL', 6, 5, '2A',  'BUSINESS', TRUE,  20750.00, 'PAID',
 now() - INTERVAL '2 days',  now() - INTERVAL '2 days'),
('VB07MD', 2, 6, '7A',  'ECONOMY',  FALSE,  5200.00, 'PAID',
 now() - INTERVAL '2 days',  now() - INTERVAL '2 days'),
('QW34ER', 3, 6, '7B',  'COMFORT',  FALSE,  8320.00, 'PAID',
 now() - INTERVAL '1 day',   now() - INTERVAL '1 day'),
('PL66CX', 4, 7, '30E', 'ECONOMY',  FALSE,  4900.00, 'CREATED',
 now() - INTERVAL '5 minutes', now() - INTERVAL '5 minutes'),
('DF52JU', 5, 7, '30F', 'ECONOMY',  TRUE,   7400.00, 'CREATED',
 now() - INTERVAL '3 minutes', now() - INTERVAL '3 minutes'),
('KJ90XZ', 6, 3, '18A', 'ECONOMY',  FALSE,  6400.00, 'CREATED',
 now() - INTERVAL '1 minute',  now() - INTERVAL '1 minute'),
('OS44BN', 1, 5, '9C',  'COMFORT',  FALSE, 13280.00, 'CANCELLED',
 now() - INTERVAL '4 days',  now() - INTERVAL '3 days'),
('WE81YT', 2, 6, '12F', 'ECONOMY',  FALSE,  5200.00, 'EXPIRED',
 now() - INTERVAL '3 days',  now() - INTERVAL '3 days' + INTERVAL '30 minutes');

-- Что демонстрирует набор данных. Пассажир № 2 имеет на рейсе № 6 две брони —
-- активную VB07MD и просроченную WE81YT. Это показывает работу частичного
-- уникального индекса uq_bookings_flight_passenger_active: правило BR-04
-- запрещает вторую активную бронь, но не мешает оформить новую после
-- аннулирования прежней. Отменённая бронь OS44BN освобождает место 9C на
-- рейсе № 5 — его можно занять повторно, что демонстрирует BR-03.
