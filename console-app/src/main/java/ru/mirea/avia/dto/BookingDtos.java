package ru.mirea.avia.dto;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO-контракты бронирований — основной сущности системы. */
public final class BookingDtos {
    private BookingDtos() {
    }

    /** Данные для оформления брони (UC-03). Стоимость не передаётся: её считает система (BR-06). */
    public record BookingRequest(
            long passengerId,
            long flightId,
            FareClass fareClass,
            String seatNumber,
            boolean baggageIncluded) {
    }

    /** Новые место, класс и багаж существующей брони (FR-08). */
    public record BookingChangeRequest(
            String seatNumber,
            FareClass fareClass,
            boolean baggageIncluded) {
    }

    /** Выходная карточка брони вместе с пассажиром и рейсом. */
    public record BookingResponse(
            long id,
            String bookingRef,
            PassengerResponse passenger,
            FlightResponse flight,
            String seatNumber,
            FareClass fareClass,
            boolean baggageIncluded,
            BigDecimal price,
            BookingStatus status,
            List<BookingStatus> allowedStatuses,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** Детализация расчёта стоимости: {@code total = basePrice × coefficient + baggageFee}. */
    public record PriceQuote(
            BigDecimal basePrice,
            FareClass fareClass,
            BigDecimal coefficient,
            BigDecimal baggageFee,
            BigDecimal total) {
    }
}
