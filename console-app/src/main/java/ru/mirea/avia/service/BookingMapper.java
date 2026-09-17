package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;

import java.util.List;

/** Преобразует бронь в публичный DTO вместе с карточками пассажира и рейса. */
public final class BookingMapper {
    private final PassengerMapper passengers;
    private final FlightMapper flights;

    public BookingMapper(PassengerMapper passengers, FlightMapper flights) {
        this.passengers = passengers;
        this.flights = flights;
    }

    /** Возвращает карточку брони со статусами, которые оператор может выбрать вручную. */
    public BookingResponse toResponse(Booking booking) {
        // EXPIRED выставляет только система по правилу BR-09, поэтому оператору он не предлагается.
        List<BookingStatus> allowedStatuses = booking.getStatus().allowedTransitions().stream()
                .filter(status -> status != BookingStatus.EXPIRED)
                .toList();
        return new BookingResponse(booking.getId(), booking.getBookingRef(),
                passengers.toResponse(booking.getPassenger()), flights.toResponse(booking.getFlight()),
                booking.getSeatNumber(), booking.getFareClass(), booking.isBaggageIncluded(),
                booking.getPrice(), booking.getStatus(), allowedStatuses,
                booking.getCreatedAt(), booking.getUpdatedAt());
    }
}
