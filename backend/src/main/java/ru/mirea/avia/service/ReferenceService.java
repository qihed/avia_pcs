package ru.mirea.avia.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.error.*;
import ru.mirea.avia.repository.*;

@Service
@Transactional
public class ReferenceService {

  private final PassengerRepository passengers;
  private final FlightRepository flights;
  private final BookingRepository bookings;
  private final Clock clock;

  public ReferenceService(
    PassengerRepository p,
    FlightRepository f,
    BookingRepository b,
    Clock c
  ) {
    passengers = p;
    flights = f;
    bookings = b;
    clock = c;
  }

  public List<Passenger> passengers() {
    return passengers.findAll(
      org.springframework.data.domain.Sort.by("lastName", "firstName", "id")
    );
  }

  public List<Flight> flights() {
    return flights.findAll(
      org.springframework.data.domain.Sort.by("departureTime", "id")
    );
  }

  public Passenger passenger(UUID id) {
    return passengers
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Пассажир не найден"));
  }

  public Flight flight(UUID id) {
    return flights
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Рейс не найден"));
  }

  public Passenger savePassenger(UUID id, PassengerRequest r) {
    Passenger p =
      id == null
        ? new Passenger()
        : passengers
            .lockById(id)
            .orElseThrow(() -> new NotFoundException("Пассажир не найден"));
    p.lastName = r.lastName().trim();
    p.firstName = r.firstName().trim();
    p.middleName = r.middleName() == null ? null : r.middleName().trim();
    p.birthDate = r.birthDate();
    p.documentType = r.documentType();
    p.documentNumber = r.documentNumber().trim();
    p.email = r.email().trim().toLowerCase(Locale.ROOT);
    p.phone = r.phone().trim();
    if (p.createdAt == null) p.createdAt = LocalDateTime.ofInstant(
      clock.instant(),
      ZoneOffset.UTC
    );
    return passengers.saveAndFlush(p);
  }

  public Flight saveFlight(UUID id, FlightRequest r) {
    Flight f =
      id == null
        ? new Flight()
        : flights
            .lockById(id)
            .orElseThrow(() -> new NotFoundException("Рейс не найден"));
    if (
      id != null && (r.version() == null || r.version() != f.version)
    ) throw new ConflictException(
      "VERSION_CONFLICT",
      "Рейс изменён. Обновите данные"
    );
    if (
      r.departureAirport().equals(r.arrivalAirport())
    ) throw new BadRequestException("Аэропорты должны различаться");
    if (
      !r.arrivalTime().isAfter(r.departureTime()) ||
      Duration.between(r.departureTime(), r.arrivalTime()).compareTo(
        Duration.ofHours(24)
      ) > 0
    ) throw new BadRequestException(
      "Продолжительность рейса должна быть от 0 до 24 часов"
    );
    if (
      id != null &&
      bookings
        .findByFlightId(id)
        .stream()
        .filter(b -> b.status.occupiesSeat())
        .anyMatch(b -> seatIndex(b.seatNumber) > r.totalSeats())
    ) throw new ConflictException("На исключаемых местах есть брони");
    f.flightNumber = r.flightNumber();
    f.airline = r.airline().trim();
    f.departureAirport = r.departureAirport();
    f.arrivalAirport = r.arrivalAirport();
    f.departureTime = r.departureTime();
    f.arrivalTime = r.arrivalTime();
    f.aircraftType = r.aircraftType().trim();
    f.totalSeats = r.totalSeats();
    f.basePrice = r.basePrice();
    f.status = r.status();
    f.active = r.active();
    f.updatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    return flights.saveAndFlush(f);
  }

  public static int seatIndex(String seat) {
    return (
      (Integer.parseInt(seat.substring(0, seat.length() - 1)) - 1) * 6 +
      seat.charAt(seat.length() - 1) -
      'A' +
      1
    );
  }

  public void deletePassenger(UUID id) {
    Passenger p = passengers
      .lockById(id)
      .orElseThrow(() -> new NotFoundException("Пассажир не найден"));
    if (bookings.existsByPassengerId(id)) throw new ConflictException(
      "Пассажир используется в бронировании"
    );
    passengers.delete(p);
    passengers.flush();
  }

  public void deleteFlight(UUID id, long version) {
    Flight f = flights
      .lockById(id)
      .orElseThrow(() -> new NotFoundException("Рейс не найден"));
    if (f.version != version) throw new ConflictException(
      "VERSION_CONFLICT",
      "Рейс изменён"
    );
    if (bookings.existsByFlightId(id)) throw new ConflictException(
      "Рейс используется в бронировании; отключите его"
    );
    flights.delete(f);
    flights.flush();
  }
}
