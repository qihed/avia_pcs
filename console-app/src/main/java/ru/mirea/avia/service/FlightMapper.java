package ru.mirea.avia.service;

import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;

import java.util.Arrays;
import java.util.List;

/** Преобразует рейс в публичный DTO вместе с маршрутом, длительностью и допустимыми статусами. */
public final class FlightMapper {
    /** Возвращает карточку рейса с производными полями и статусами, доступными для перехода. */
    public FlightResponse toResponse(Flight flight) {
        List<FlightStatus> allowedStatuses = Arrays.stream(FlightStatus.values())
                .filter(flight.getStatus()::canTransitionTo)
                .toList();
        return new FlightResponse(flight.getId(), flight.getFlightNumber(), flight.getAirline(),
                flight.getDepartureAirport(), flight.getArrivalAirport(), flight.getRoute(),
                flight.getDepartureTime(), flight.getArrivalTime(), flight.getDuration(),
                flight.getAircraftType(), flight.getTotalSeats(), flight.getRowCount(),
                flight.getBasePrice(), flight.getStatus(), allowedStatuses);
    }
}
