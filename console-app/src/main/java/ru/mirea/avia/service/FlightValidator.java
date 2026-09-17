package ru.mirea.avia.service;

import ru.mirea.avia.dto.FlightDtos.FlightRequest;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Проверяет и нормализует данные рейса.
 *
 * <p>Повторяет ограничения таблицы {@code flights} ({@code chk_flights_*}), чтобы оператор
 * получил понятное сообщение ещё до обращения к СУБД.</p>
 */
public final class FlightValidator {
    public static final int MIN_SEATS = 1;
    public static final int MAX_SEATS = 600;
    /** Верхняя граница тарифа: цена бизнес-класса (×2,5) должна помещаться в NUMERIC(10,2). */
    public static final BigDecimal MAX_BASE_PRICE = new BigDecimal("1000000.00");
    private static final int MAX_AIRLINE_LENGTH = 60;
    private static final int MAX_AIRCRAFT_LENGTH = 40;
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("^([A-Z]{2}|[A-Z]\\d|\\d[A-Z])\\d{1,4}$");
    private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

    /** Проверяет номер рейса: код авиакомпании из 2 символов и 1–4 цифры (SU1416, U62810). */
    public String normalizeFlightNumber(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "Номер рейса не может быть пустым");
        }
        if (!FLIGHT_NUMBER.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.E_103,
                    "Номер рейса должен иметь вид SU1416 (код авиакомпании и 1–4 цифры)");
        }
        return normalized;
    }

    /** Проверяет код аэропорта IATA: три латинские буквы. */
    public String normalizeAirport(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!IATA.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.E_103,
                    "Код аэропорта должен состоять из трёх латинских букв, например SVO");
        }
        return normalized;
    }

    /** Проверяет обязательное текстовое поле и сжимает в нём пробелы. */
    public String requireText(String value, String fieldTitle, int maxLength) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new ValidationException(ErrorCode.E_104, "Поле «" + fieldTitle + "» не может быть пустым");
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(ErrorCode.E_103,
                    "Поле «" + fieldTitle + "» длиннее " + maxLength + " символов");
        }
        return trimmed;
    }

    /** Проверяет, что аэропорт назначения отличается от аэропорта вылета. */
    public void checkRoute(String departureAirport, String arrivalAirport) {
        if (departureAirport.equals(arrivalAirport)) {
            throw new ValidationException(ErrorCode.E_103,
                    "Аэропорт назначения должен отличаться от аэропорта вылета");
        }
    }

    /** Проверяет, что время прилёта позже времени вылета. */
    public void checkSchedule(LocalDateTime departure, LocalDateTime arrival) {
        if (departure == null || arrival == null) {
            throw new ValidationException(ErrorCode.E_104, "Время вылета и прилёта должно быть заполнено");
        }
        if (!arrival.isAfter(departure)) {
            throw new ValidationException(ErrorCode.E_102, "Время прилёта должно быть позже времени вылета");
        }
    }

    /** Проверяет, что число мест лежит в диапазоне от {@link #MIN_SEATS} до {@link #MAX_SEATS}. */
    public int checkSeats(int totalSeats) {
        if (totalSeats < MIN_SEATS || totalSeats > MAX_SEATS) {
            throw new ValidationException(ErrorCode.E_103,
                    "Число мест должно быть от " + MIN_SEATS + " до " + MAX_SEATS);
        }
        return totalSeats;
    }

    /** Проверяет, что базовый тариф больше нуля и не превышает {@link #MAX_BASE_PRICE}. */
    public BigDecimal checkBasePrice(BigDecimal basePrice) {
        if (basePrice == null) {
            throw new ValidationException(ErrorCode.E_104, "Базовый тариф не может быть пустым");
        }
        if (basePrice.signum() <= 0 || basePrice.compareTo(MAX_BASE_PRICE) > 0) {
            throw new ValidationException(ErrorCode.E_103,
                    "Базовый тариф должен быть больше нуля и не больше 1 000 000,00 руб.");
        }
        return basePrice;
    }

    /** Проверяет рейс целиком и возвращает копию запроса с нормализованными полями. */
    public FlightRequest validate(FlightRequest request) {
        String flightNumber = normalizeFlightNumber(request.flightNumber());
        String airline = requireText(request.airline(), "Авиакомпания", MAX_AIRLINE_LENGTH);
        String from = normalizeAirport(request.departureAirport());
        String to = normalizeAirport(request.arrivalAirport());
        checkRoute(from, to);
        checkSchedule(request.departureTime(), request.arrivalTime());
        String aircraftType = requireText(request.aircraftType(), "Тип воздушного судна", MAX_AIRCRAFT_LENGTH);
        return new FlightRequest(flightNumber, airline, from, to, request.departureTime(), request.arrivalTime(),
                aircraftType, checkSeats(request.totalSeats()), checkBasePrice(request.basePrice()));
    }
}
