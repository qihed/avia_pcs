package ru.mirea.avia.dto;

import ru.mirea.avia.domain.FlightStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** DTO-контракты расписания рейсов. */
public final class FlightDtos {
    private FlightDtos() {
    }

    /** Входная модель создания и изменения рейса. */
    public record FlightRequest(
            String flightNumber,
            String airline,
            String departureAirport,
            String arrivalAirport,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            String aircraftType,
            int totalSeats,
            BigDecimal basePrice) {
    }

    /** Выходная карточка рейса. */
    public record FlightResponse(
            long id,
            String flightNumber,
            String airline,
            String departureAirport,
            String arrivalAirport,
            String route,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            Duration duration,
            String aircraftType,
            int totalSeats,
            int rowCount,
            BigDecimal basePrice,
            FlightStatus status,
            List<FlightStatus> allowedStatuses) {

        /** Возвращает запрос с текущими значениями рейса — основа диалога изменения. */
        public FlightRequest toRequest() {
            return new FlightRequest(flightNumber, airline, departureAirport, arrivalAirport, departureTime,
                    arrivalTime, aircraftType, totalSeats, basePrice);
        }
    }

    /** Рейс вместе с загрузкой — колонка «Свободно / Всего» (US-01). */
    public record FlightAvailability(
            FlightResponse flight,
            int occupiedSeats,
            int freeSeats,
            boolean openForSale) {
    }
}
