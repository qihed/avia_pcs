package ru.mirea.avia.service;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.dto.BookingBulkDtos.BulkFailure;
import ru.mirea.avia.dto.BookingBulkDtos.BulkRequest;
import ru.mirea.avia.dto.BookingBulkDtos.BulkResult;
import ru.mirea.avia.dto.BookingBulkDtos.TagUsage;
import ru.mirea.avia.error.*;
import ru.mirea.avia.filter.BookingFilter;
import ru.mirea.avia.filter.BookingSpecifications;
import ru.mirea.avia.filter.BookingTags;
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
  private final TransactionTemplate bulkTransaction;
  private final SecureRandom random = new SecureRandom();

  public BookingService(
    BookingRepository b,
    FlightRepository f,
    PassengerRepository p,
    BookingNoteRepository n,
    Clock c,
    AppProperties a,
    PlatformTransactionManager transactions
  ) {
    bookings = b;
    flights = f;
    passengers = p;
    notes = n;
    clock = c;
    props = a;
    bulkTransaction = new TransactionTemplate(transactions);
    bulkTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private void expire() {
    bookings.expireBefore(now().minusMinutes(30), now());
  }

  /** Поиск, фильтры, сортировка и пагинация: условия собирает BookingSpecifications. */
  public PageResponse<BookingResponse> list(
    BookingFilter filter,
    Pageable pageable
  ) {
    filter.validate();
    expire();
    return PageResponse.from(
      bookings
        .findAll(BookingSpecifications.from(filter, now()), pageable)
        .map(b -> BookingResponse.of(b, clock))
    );
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
    applyStatus(b, r.status());
    return saved(b);
  }

  /** Правила смены статуса; общие для одной брони и массовой операции. */
  private void applyStatus(Booking b, BookingStatus target) {
    lockFlight(b.flight.id);
    if (
      b.archived || !b.status.canChangeTo(target)
    ) throw new ConflictException("Недопустимый переход статуса");
    if (
      target == BookingStatus.CANCELLED &&
      now().isAfter(b.flight.departureTime.minusHours(2))
    ) throw new ConflictException(
      "До вылета менее двух часов: отмена невозможна"
    );
    if (
      target == BookingStatus.PAID || target == BookingStatus.CHECKED_IN
    ) open(b.flight);
    b.status = target;
  }

  public BookingResponse archive(UUID id, long version, boolean archived) {
    expire();
    Booking b = version(id, version);
    applyArchive(b, archived);
    return saved(b);
  }

  private void applyArchive(Booking b, boolean archived) {
    if (b.archived == archived) throw new ConflictException(
      "Бронь уже находится в запрошенном состоянии"
    );
    if (archived && !b.status.terminal()) throw new ConflictException(
      "Архивировать можно только завершённую, отменённую или просроченную бронь"
    );
    b.archived = archived;
  }

  // ---------------------------------------------------------------------
  // Теги и массовые операции
  // ---------------------------------------------------------------------

  /** Заменяет набор тегов брони. Архивная бронь только для чтения, как и её заметки. */
  public BookingResponse setTags(UUID id, List<String> tags, Long version) {
    expire();
    Booking b = version(id, version);
    if (b.archived) throw new ConflictException(
      "Теги архивной брони менять нельзя"
    );
    SortedSet<String> normalized = new TreeSet<>();
    List<FieldViolation> errors = new ArrayList<>();
    for (String raw : tags) {
      String tag = BookingTags.normalize(raw);
      String problem = BookingTags.problem(tag);
      if (problem != null) errors.add(new FieldViolation("tags", problem));
      else normalized.add(tag);
    }
    if (normalized.size() > BookingTags.MAX_PER_BOOKING) errors.add(
      new FieldViolation(
        "tags",
        "Не больше " + BookingTags.MAX_PER_BOOKING + " тегов у одной брони"
      )
    );
    if (!errors.isEmpty()) throw new ApiException(
      org.springframework.http.HttpStatus.BAD_REQUEST,
      "INVALID_TAGS",
      "Проверьте теги",
      errors
    );
    b.tags.clear();
    b.tags.addAll(normalized);
    return saved(b);
  }

  @Transactional(readOnly = true)
  public List<TagUsage> tagUsage() {
    return bookings
      .tagUsageRows()
      .stream()
      .map(row -> new TagUsage((String) row[0], ((Number) row[1]).longValue()))
      .toList();
  }

  /**
   * Одно действие над выбранными бронями. Каждая бронь проверяется теми же правилами, что и при
   * одиночной операции; если бронь не подходит, она попадает в failures, а остальные изменяются.
   * Каждая бронь обрабатывается в своей транзакции: отказ или конфликт версии откатывает только её.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public BulkResult bulk(BulkRequest r) {
    String tag = null;
    switch (r.action()) {
      case SET_STATUS -> {
        if (r.status() == null) throw new BadRequestException(
          "Для смены статуса укажите status"
        );
      }
      case ADD_TAG, REMOVE_TAG -> {
        tag = BookingTags.normalize(r.tag());
        String problem = BookingTags.problem(tag);
        if (problem != null) throw new ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "INVALID_TAGS",
          problem,
          List.of(new FieldViolation("tag", problem))
        );
      }
      default -> {}
    }
    bulkTransaction.executeWithoutResult(tx -> expire());
    List<UUID> ids = List.copyOf(new LinkedHashSet<>(r.ids()));
    int succeeded = 0;
    List<BulkFailure> failures = new ArrayList<>();
    String normalizedTag = tag;
    for (UUID id : ids) {
      try {
        BulkFailure failure = bulkTransaction.execute(tx -> {
          Booking b = bookings.findById(id).orElse(null);
          if (b == null) return new BulkFailure(id, null, "Бронь не найдена");
          try {
            if (applyBulk(b, r, normalizedTag)) saved(b);
            return null;
          } catch (ApiException e) {
            tx.setRollbackOnly();
            return new BulkFailure(id, b.bookingCode, e.getMessage());
          }
        });
        if (failure == null) succeeded++;
        else failures.add(failure);
      } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
        failures.add(new BulkFailure(id, null, "Бронь изменена другим пользователем. Обновите данные"));
      } catch (DataIntegrityViolationException e) {
        failures.add(new BulkFailure(id, null, "Операция нарушает ограничения данных"));
      }
    }
    return new BulkResult(ids.size(), succeeded, failures);
  }

  /** @return true, если бронь изменилась и её нужно сохранить */
  private boolean applyBulk(Booking b, BulkRequest r, String tag) {
    switch (r.action()) {
      case SET_STATUS -> applyStatus(b, r.status());
      case ARCHIVE -> applyArchive(b, true);
      case RESTORE -> applyArchive(b, false);
      case ADD_TAG -> {
        if (b.archived) throw new ConflictException(
          "Теги архивной брони менять нельзя"
        );
        if (b.tags.contains(tag)) return false;
        if (b.tags.size() >= BookingTags.MAX_PER_BOOKING) throw new ConflictException(
          "У брони уже " + BookingTags.MAX_PER_BOOKING + " тегов"
        );
        b.tags.add(tag);
      }
      case REMOVE_TAG -> {
        if (b.archived) throw new ConflictException(
          "Теги архивной брони менять нельзя"
        );
        if (!b.tags.remove(tag)) return false;
      }
    }
    return true;
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
