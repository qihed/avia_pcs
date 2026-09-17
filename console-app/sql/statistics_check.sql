-- =====================================================================
-- Контрольные SQL-запросы для тест-кейса TC-16.
-- Значения должны совпасть с разделом «6. Статистика» приложения.
-- Запуск: psql -U postgres -d avia_booking -f sql/statistics_check.sql
-- =====================================================================

SET search_path TO avia;

-- ST-01 Всего пассажиров
SELECT count(*) AS st01_passengers FROM passengers;

-- ST-02 Всего рейсов / доступных для продажи
SELECT count(*) AS st02_flights,
       count(*) FILTER (WHERE status IN ('SCHEDULED', 'DELAYED')) AS st02_open_for_sale
FROM flights;

-- ST-03 Всего бронирований
SELECT count(*) AS st03_bookings FROM bookings;

-- ST-04 Распределение по статусам
SELECT status, count(*) AS cnt FROM bookings GROUP BY status ORDER BY status;

-- ST-05 Распределение по классам обслуживания (количество и доля, %)
SELECT fare_class, count(*) AS cnt,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS share_pct
FROM bookings GROUP BY fare_class ORDER BY fare_class;

-- ST-06 Суммарная выручка и ST-07 средняя стоимость оплаченного билета
SELECT sum(price)           AS st06_revenue,
       round(avg(price), 2) AS st07_avg_price
FROM bookings
WHERE status IN ('PAID', 'CHECKED_IN', 'COMPLETED');

-- ST-08 Средняя загрузка рейсов, % (активные брони / суммарная вместимость)
SELECT round(100.0 * (SELECT count(*) FROM bookings
                      WHERE status NOT IN ('CANCELLED', 'EXPIRED'))
             / (SELECT sum(total_seats) FROM flights), 1) AS st08_load_pct;

-- ST-09 Топ-5 рейсов по числу броней
SELECT f.flight_number, f.departure_airport || ' -> ' || f.arrival_airport AS route,
       f.departure_time, count(*) AS cnt
FROM bookings b JOIN flights f ON f.id = b.flight_id
GROUP BY f.id
ORDER BY cnt DESC, f.departure_time
LIMIT 5;

-- ST-10 Топ-3 направления
SELECT f.departure_airport || ' -> ' || f.arrival_airport AS route, count(*) AS cnt
FROM bookings b JOIN flights f ON f.id = b.flight_id
GROUP BY f.departure_airport, f.arrival_airport
ORDER BY cnt DESC, min(f.departure_time)
LIMIT 3;

-- ST-11 Доля отменённых и просроченных броней, %
SELECT round(100.0 * count(*) FILTER (WHERE status IN ('CANCELLED', 'EXPIRED'))
             / count(*), 1) AS st11_cancelled_pct
FROM bookings;
