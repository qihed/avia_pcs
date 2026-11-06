-- V2: расширение базы КР1 под REST backend (КР3).
-- Здесь собраны ВСЕ новые таблицы и поля: users, refresh_sessions, bookings, tags, notes, audit.
-- Нужно новое поле или таблица — пишите лиду, он добавит миграцию V3, V4 и т.д.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- 1. Первичные ключи passengers / flights / bookings: BIGSERIAL -> UUID
-- ---------------------------------------------------------------------
ALTER TABLE passengers ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE flights    ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE bookings   ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid(),
                       ADD COLUMN passenger_uid UUID,
                       ADD COLUMN flight_uid    UUID;

UPDATE bookings b
   SET passenger_uid = p.uid,
       flight_uid    = f.uid
  FROM passengers p, flights f
 WHERE p.id = b.passenger_id
   AND f.id = b.flight_id;

ALTER TABLE bookings DROP CONSTRAINT bookings_passenger_id_fkey;
ALTER TABLE bookings DROP CONSTRAINT bookings_flight_id_fkey;
ALTER TABLE bookings DROP COLUMN passenger_id, DROP COLUMN flight_id, DROP COLUMN id;
ALTER TABLE passengers DROP COLUMN id;
ALTER TABLE flights    DROP COLUMN id;

ALTER TABLE passengers RENAME COLUMN uid TO id;
ALTER TABLE flights    RENAME COLUMN uid TO id;
ALTER TABLE bookings   RENAME COLUMN uid TO id;
ALTER TABLE bookings   RENAME COLUMN passenger_uid TO passenger_id;
ALTER TABLE bookings   RENAME COLUMN flight_uid    TO flight_id;

ALTER TABLE passengers ADD PRIMARY KEY (id);
ALTER TABLE flights    ADD PRIMARY KEY (id);
ALTER TABLE bookings   ADD PRIMARY KEY (id);

ALTER TABLE bookings ALTER COLUMN passenger_id SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN flight_id    SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_passenger FOREIGN KEY (passenger_id) REFERENCES passengers (id);
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_flight    FOREIGN KEY (flight_id)    REFERENCES flights (id);

-- ---------------------------------------------------------------------
-- 2. Бронирование: новые поля
-- ---------------------------------------------------------------------
ALTER TABLE bookings
    ADD COLUMN booking_code  VARCHAR(6),
    ADD COLUMN extra_baggage BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN version       BIGINT    NOT NULL DEFAULT 0,
    ADD COLUMN archived      BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at    TIMESTAMP NOT NULL DEFAULT now();

UPDATE bookings SET booking_code = upper(substr(md5(id::text), 1, 6));

ALTER TABLE bookings ALTER COLUMN booking_code SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT uq_bookings_code UNIQUE (booking_code);
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_code_format CHECK (booking_code ~ '^[A-Z0-9]{6}$');

-- ---------------------------------------------------------------------
-- 3. Справочник рейсов: включение / отключение без удаления
-- ---------------------------------------------------------------------
ALTER TABLE flights
    ADD COLUMN active     BOOLEAN   NOT NULL DEFAULT TRUE,
    ADD COLUMN version    BIGINT    NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT now();

-- ---------------------------------------------------------------------
-- 4. Пользователи, роли, refresh-сессии
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login                VARCHAR(120)  NOT NULL,
    display_name         VARCHAR(120)  NOT NULL,
    password_hash        VARCHAR(100)  NOT NULL,
    enabled              BOOLEAN       NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_attempts      INTEGER       NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_users_login_lower ON users (lower(login));
CREATE INDEX idx_users_display_name_trgm ON users USING gin (lower(display_name) gin_trgm_ops);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_roles_role CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN'))
);

CREATE TABLE refresh_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(200)
);
CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id);

-- ---------------------------------------------------------------------
-- 5. Теги и заметки брони
-- ---------------------------------------------------------------------
CREATE TABLE booking_tags (
    booking_id UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    tag        VARCHAR(40) NOT NULL,
    PRIMARY KEY (booking_id, tag),
    CONSTRAINT chk_booking_tags_not_blank CHECK (length(btrim(tag)) > 0)
);
CREATE INDEX idx_booking_tags_tag ON booking_tags (lower(tag));

CREATE TABLE booking_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    author_id   UUID          REFERENCES users (id) ON DELETE SET NULL,
    author_name VARCHAR(120)  NOT NULL,
    text        VARCHAR(2000) NOT NULL,
    pinned      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_notes_not_blank CHECK (length(btrim(text)) > 0)
);
CREATE INDEX idx_booking_notes_booking ON booking_notes (booking_id, pinned DESC, created_at DESC);

-- ---------------------------------------------------------------------
-- 6. Журнал аудита
-- ---------------------------------------------------------------------
CREATE TABLE audit_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID,
    username       VARCHAR(120),
    event_type     VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      UUID,
    description    VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created_at  ON audit_events (created_at DESC);
CREATE INDEX idx_audit_event_type  ON audit_events (event_type);
CREATE INDEX idx_audit_entity      ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_username    ON audit_events (lower(username));
CREATE INDEX idx_audit_description_trgm ON audit_events USING gin (lower(description) gin_trgm_ops);

-- ---------------------------------------------------------------------
-- 7. Индексы для поиска и фильтров
-- ---------------------------------------------------------------------
CREATE INDEX idx_bookings_flight        ON bookings (flight_id);
CREATE INDEX idx_bookings_passenger     ON bookings (passenger_id);
CREATE INDEX idx_bookings_status        ON bookings (status);
CREATE INDEX idx_bookings_archived      ON bookings (archived);
CREATE INDEX idx_bookings_price         ON bookings (price);
CREATE INDEX idx_bookings_created_at    ON bookings (created_at DESC);
CREATE INDEX idx_bookings_code_trgm     ON bookings USING gin (lower(booking_code) gin_trgm_ops);
CREATE INDEX idx_passengers_last_name   ON passengers USING gin (lower(last_name) gin_trgm_ops);
CREATE INDEX idx_flights_number_trgm    ON flights USING gin (lower(flight_number) gin_trgm_ops);
CREATE INDEX idx_flights_active         ON flights (active);
