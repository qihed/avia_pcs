# ER-диаграмма базы данных

Схема `avia`, PostgreSQL 16. Схему создаёт Flyway при старте приложения миграцией
[`db/migration/V1__initial_schema.sql`](../console-app/src/main/resources/db/migration/V1__initial_schema.sql).
Тот же DDL для ручного запуска через psql — [`console-app/sql/schema.sql`](../console-app/sql/schema.sql)
(`scripts/verify.py` проверяет, что оба файла совпадают).

![ER-диаграмма](er-diagram.png)

Векторная версия — [er-diagram.svg](er-diagram.svg).

Та же модель в Mermaid (отрисовывается на GitHub / GitLab):

```mermaid
erDiagram
    passengers ||--o{ bookings : "fk_bookings_passenger (ON DELETE RESTRICT)"
    flights    ||--o{ bookings : "fk_bookings_flight (ON DELETE RESTRICT)"

    passengers {
        BIGSERIAL    id               PK
        VARCHAR_60   last_name        "NOT NULL, CHECK не пустая"
        VARCHAR_60   first_name       "NOT NULL, CHECK не пустое"
        VARCHAR_60   middle_name
        DATE         birth_date       "NOT NULL, CHECK 1900..2100"
        VARCHAR_24   document_type    "NOT NULL, UQ с document_number"
        VARCHAR_20   document_number  "NOT NULL, UQ с document_type"
        VARCHAR_120  email            "NOT NULL, UNIQUE, CHECK формат"
        VARCHAR_20   phone            "NOT NULL"
        TIMESTAMP    created_at       "NOT NULL"
    }

    flights {
        BIGSERIAL    id                PK
        VARCHAR_8    flight_number     "NOT NULL, UQ с departure_time"
        VARCHAR_60   airline           "NOT NULL"
        CHAR_3       departure_airport "NOT NULL, IATA"
        CHAR_3       arrival_airport   "NOT NULL, IATA, <> вылета"
        TIMESTAMP    departure_time    "NOT NULL"
        TIMESTAMP    arrival_time      "NOT NULL, > вылета"
        VARCHAR_40   aircraft_type     "NOT NULL"
        INTEGER      total_seats       "NOT NULL, 1..600"
        NUMERIC_10_2 base_price        "NOT NULL, > 0"
        VARCHAR_16   status            "NOT NULL, FlightStatus"
    }

    bookings {
        BIGSERIAL    id               PK
        CHAR_6       booking_ref      "NOT NULL, UNIQUE, [A-Z0-9]{6}"
        BIGINT       passenger_id     FK
        BIGINT       flight_id        FK
        VARCHAR_4    seat_number      "NOT NULL, ряд + A-F"
        VARCHAR_10   fare_class       "NOT NULL, FareClass"
        BOOLEAN      baggage_included "NOT NULL"
        NUMERIC_10_2 price            "NOT NULL, > 0"
        VARCHAR_12   status           "NOT NULL, BookingStatus"
        TIMESTAMP    created_at       "NOT NULL"
        TIMESTAMP    updated_at       "NOT NULL"
    }
```

Связь «многие ко многим» между пассажирами и рейсами разрешается таблицей `bookings`,
которая является полноценной сущностью (место, класс, цена, статус, PNR).

Частичные уникальные индексы действуют только для броней в статусах, отличных от `CANCELLED` и `EXPIRED`:

| Индекс | Поля | Правило |
|---|---|---|
| `uq_bookings_flight_seat_active` | `flight_id, seat_number` | BR-03 — одно место на рейсе занято не более чем одной активной бронью |
| `uq_bookings_flight_passenger_active` | `flight_id, passenger_id` | BR-04 — у пассажира не более одной активной брони на рейс |

Кроме трёх таблиц предметной области, в схеме `avia` есть служебная таблица Flyway
`flyway_schema_history` — список применённых миграций (V1 — схема, V2 — демонстрационные данные).
На диаграмме она не показана.
