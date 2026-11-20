package ru.mirea.avia.service;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.error.*;
import ru.mirea.avia.repository.*;

@Service
@Transactional
public class BookingService {

  private final BookingRepository bookings;
  private final FlightRepository flights;
  private final PassengerRepository passengers;
  private final BookingNoteRepository notes;
  private final Clock clock;
  private final AppProperties props;
  private final SecureRandom random = new SecureRandom();

  public BookingService(
    BookingRepository b,
    FlightRepository f,
    PassengerRepository p,
    BookingNoteRepository n,
    Clock c,
    AppProperties a
  ) {
    bookings = b;
    flights = f;
    passengers = p;
    notes = n;
    clock = c;
    props = a;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private void expire() {
    bookings.expireBefore(now().minusMinutes(30), now());
  }

  public Page<BookingResponse> list(boolean archived, int page, int size) {
    if (
      page < 0 || size < 1 || size > props.pagination().maxPageSize()
    ) throw new BadRequestException("Некорректный размер страницы");
    expire();
    return bookings
      .findByArchived(
        archived,
        PageRequest.of(
          page,
          size,
          Sort.by(Sort.Direction.DESC, "createdAt", "id")
        )
      )
      .map(b -> BookingResponse.of(b, clock));
  }

  public BookingResponse get(UUID id) {
    expire();
    return BookingResponse.of(require(id), clock);
  }

  private Booking require(UUID id) {
    return bookings
      .findById(id)
      .orElseThrow(() -> new NotFoundException("Бронь не найдена"));
  }

  private Booking version(UUID id, Long version) {
    Booking b = require(id);
    if (version == null) throw new BadRequestException("Укажите version");
    if (b.version != version) throw new ConflictException(
      "VERSION_CONFLICT",
      "Бронь изменена другим пользователем. Обновите данные"
    );
    return b;
  }

  private Flight lockFlight(UUID id) {
    return flights
      .lockById(id)
      .orElseThrow(() -> new NotFoundException("Рейс не найден"));
  }

  private void open(Flight f) {
    if (
      !f.active ||
      (f.status != FlightStatus.SCHEDULED &&
        f.status != FlightStatus.DELAYED) ||
      !f.departureTime.isAfter(now())
    ) throw new ConflictException("Рейс закрыт для продажи");
  }

  private void checkSeat(Booking b, String seat) {
    if (
      ReferenceService.seatIndex(seat) > b.flight.totalSeats
    ) throw new BadRequestException("Места нет в самолёте");
    List<Booking> others = bookings
      .findByFlightId(b.flight.id)
      .stream()
      .filter(x -> !Objects.equals(x.id, b.id) && x.status.occupiesSeat())
      .toList();
    if (others.size() >= b.flight.totalSeats) throw new ConflictException(
      "Свободных мест нет"
    );
    if (
      others.stream().anyMatch(x -> x.seatNumber.equals(seat))
    ) throw new ConflictException("SEAT_TAKEN", "Место уже занято");
    if (
      others.stream().anyMatch(x -> x.passenger.id.equals(b.passenger.id))
    ) throw new ConflictException(
      "DUPLICATE_BOOKING",
      "У пассажира уже есть бронь на этот рейс"
    );
  }

  private String code() {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    for (int attempt = 0; attempt < 20; attempt++) {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < 6; i++) s.append(
        alphabet.charAt(random.nextInt(alphabet.length()))
      );
      if (!bookings.existsByBookingCode(s.toString())) return s.toString();
    }
    throw new ConflictException(
      "Не удалось создать код брони. Повторите операцию"
    );
  }

  private BookingResponse saved(Booking b) {
    b.updatedAt = now();
    return BookingResponse.of(bookings.saveAndFlush(b), clock);
  }

  public BookingResponse create(BookingRequest r) {
    expire();
    Flight f = lockFlight(r.flightId());
    open(f);
    Passenger p = passengers
      .findById(r.passengerId())
      .orElseThrow(() -> new NotFoundException("Пассажир не найден"));
    Booking b = new Booking();
    b.flight = f;
    b.passenger = p;
    b.seatNumber = r.seatNumber();
    b.fareClass = r.fareClass();
    b.price = r.price();
    b.extraBaggage = r.extraBaggage();
    b.bookingCode = code();
    b.createdAt = now();
    checkSeat(b, b.seatNumber);
    return saved(b);
  }

  public BookingResponse update(UUID id, BookingRequest r) {
    expire();
    Booking b = version(id, r.version());
    lockFlight(b.flight.id);
    if (
      b.archived ||
      (b.status != BookingStatus.CREATED && b.status != BookingStatus.PAID)
    ) throw new ConflictException(
      "Изменить можно только активную созданную или оплаченную бронь"
    );
    if (
      !b.flight.id.equals(r.flightId()) ||
      !b.passenger.id.equals(r.passengerId())
    ) throw new BadRequestException(
      "Для другого пассажира или рейса создайте новую бронь"
    );
    open(b.flight);
    checkSeat(b, r.seatNumber());
    b.seatNumber = r.seatNumber();
    b.fareClass = r.fareClass();
    b.price = r.price();
    b.extraBaggage = r.extraBaggage();
    return saved(b);
  }

  public BookingResponse status(UUID id, StatusRequest r) {
    expire();
    Booking b = version(id, r.version());
    lockFlight(b.flight.id);
    if (
      b.archived || !b.status.canChangeTo(r.status())
    ) throw new ConflictException("Недопустимый переход статуса");
    if (
      r.status() == BookingStatus.CANCELLED &&
      now().isAfter(b.flight.departureTime.minusHours(2))
    ) throw new ConflictException(
      "До вылета менее двух часов: отмена невозможна"
    );
    if (
      r.status() == BookingStatus.PAID || r.status() == BookingStatus.CHECKED_IN
    ) open(b.flight);
    b.status = r.status();
    return saved(b);
  }

  public BookingResponse archive(UUID id, long version, boolean archived) {
    expire();
    Booking b = version(id, version);
    if (b.archived == archived) throw new ConflictException(
      "Бронь уже находится в запрошенном состоянии"
    );
    if (archived && !b.status.terminal()) throw new ConflictException(
      "Архивировать можно только завершённую, отменённую или просроченную бронь"
    );
    b.archived = archived;
    return saved(b);
  }

  public void permanent(UUID id, long version) {
    if (!props.permanentDeleteEnabled()) throw new ForbiddenException(
      "Окончательное удаление отключено настройкой сервера"
    );
    Booking b = version(id, version);
    if (!b.archived) throw new ConflictException(
      "Сначала перенесите бронь в архив"
    );
    notes.deleteByBookingId(id);
    bookings.delete(b);
    bookings.flush();
  }
}
