package ru.mirea.avia.support;

import ru.mirea.avia.config.AppProperties.BookingRules;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.filter.SmartFilterParser;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.service.BookingMapper;
import ru.mirea.avia.service.BookingSearchService;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.BookingValidator;
import ru.mirea.avia.service.FareCalculator;
import ru.mirea.avia.service.FlightMapper;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.FlightValidator;
import ru.mirea.avia.service.PassengerMapper;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.service.PassengerValidator;
import ru.mirea.avia.service.PnrGenerator;
import ru.mirea.avia.service.StatisticsService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Сервисы на репозиториях в памяти с фиксированными часами.
 *
 * <p>Набор данных повторяет приложение Б ТЗ (6 пассажиров, 8 рейсов, 14 броней) с датами
 * относительно «сегодня», как {@code db/demo/V2__demo_data.sql}.</p>
 */
public final class TestContext {
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    public static final LocalDateTime DEFAULT_NOW = LocalDateTime.of(2026, 9, 16, 15, 0);
    public static final Duration SALE_CLOSE = Duration.ofMinutes(60);
    public static final Duration CANCEL_LIMIT = Duration.ofHours(2);
    public static final Duration EXPIRE_AFTER = Duration.ofMinutes(30);

    public final LocalDateTime now;
    public final Clock clock;
    public final InMemoryPassengerRepository passengers = new InMemoryPassengerRepository();
    public final InMemoryFlightRepository flights = new InMemoryFlightRepository();
    public final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    public final FlightMapper flightMapper = new FlightMapper();
    public final PassengerMapper passengerMapper;
    public final BookingMapper bookingMapper;
    public final BookingValidator bookingValidator;
    public final FlightValidator flightValidator = new FlightValidator();
    public final PassengerService passengerService;
    public final FlightService flightService;
    public final BookingService bookingService;
    public final BookingSearchService searchService;
    public final StatisticsService statisticsService;

    public TestContext() {
        this(DEFAULT_NOW);
    }

    public TestContext(LocalDateTime now) {
        this.now = now;
        this.clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        Transactions transactions = Transactions.direct();
        passengerMapper = new PassengerMapper(clock);
        bookingMapper = new BookingMapper(passengerMapper, flightMapper);
        bookingValidator = new BookingValidator(bookings, clock, new BookingRules(SALE_CLOSE, CANCEL_LIMIT, EXPIRE_AFTER));
        passengerService = new PassengerService(passengers, bookings, new PassengerValidator(clock), passengerMapper,
                transactions, clock);
        flightService = new FlightService(flights, bookings, flightValidator, flightMapper, transactions, clock, SALE_CLOSE);
        bookingService = new BookingService(bookings, passengers, flights, bookingValidator, new FareCalculator(),
                new PnrGenerator(new Random(42)), bookingMapper, transactions, clock, EXPIRE_AFTER);
        searchService = new BookingSearchService(bookings, flights, bookingValidator, flightValidator,
                new SmartFilterParser(), bookingMapper, transactions);
        statisticsService = new StatisticsService(passengers, flights, bookings, flightMapper, transactions);
    }

    /** Загружает набор данных {@code db/demo/V2__demo_data.sql}. */
    public TestContext withDemoData() {
        passengers();
        flight("SU1416", "SVO", "LED", -8, "08:40", 80, "Airbus A320", 180, "6400", FlightStatus.ARRIVED);
        flight("SU1102", "SVO", "AER", -6, "19:05", 150, "Boeing 737-800", 128, "8300", FlightStatus.ARRIVED);
        flight("SU1420", "SVO", "LED", 1, "08:40", 80, "Airbus A320", 180, "6400", FlightStatus.SCHEDULED);
        flight("SU1421", "LED", "SVO", 1, "19:20", 80, "Airbus A320", 180, "6400", FlightStatus.SCHEDULED);
        flight("SU1108", "SVO", "AER", 7, "19:05", 150, "Boeing 737-800", 128, "8300", FlightStatus.SCHEDULED);
        flight("FV6021", "LED", "KZN", 11, "11:30", 130, "Airbus A319", 156, "5200", FlightStatus.SCHEDULED);
        flight("U62810", "SVO", "KZN", 13, "06:50", 95, "Airbus A321", 220, "4900", FlightStatus.DELAYED);
        flight("SU1130", "SVO", "AER", 17, "09:00", 150, "Boeing 737-800", 128, "9200", FlightStatus.CANCELLED);
        bookings();
        return this;
    }

    public Passenger passenger(String last, String first, String middle, String birth, DocumentType type,
                               String number, String login) {
        Passenger passenger = new Passenger(last, first, LocalDate.parse(birth), type, number, login + "@example.com",
                "+7916100" + String.format("%04d", passengers.count()));
        passenger.setMiddleName(middle);
        passenger.setCreatedAt(now.minusDays(30));
        return passengers.insert(passenger);
    }

    public Flight flight(String number, String from, String to, int dayOffset, String time, int minutes,
                         String aircraft, int seats, String basePrice, FlightStatus status) {
        LocalDateTime departure = now.toLocalDate().plusDays(dayOffset).atTime(LocalTime.parse(time));
        return flightAt(number, from, to, departure, minutes, seats, basePrice, status);
    }

    public Flight flightAt(String number, String from, String to, LocalDateTime departure, int minutes,
                           int seats, String basePrice, FlightStatus status) {
        Flight flight = new Flight(number, "Аэрофлот", from, to, departure, departure.plusMinutes(minutes),
                "Airbus A320", seats, new BigDecimal(basePrice));
        flight.setStatus(status);
        return flights.insert(flight);
    }

    /** Добавляет бронь, созданную {@code minutesAgo} минут назад, в произвольном статусе (как в data.sql). */
    public Booking booking(String ref, long passengerId, long flightId, String seat, FareClass fareClass,
                           boolean baggage, String price, BookingStatus status, long minutesAgo) {
        Booking booking = new Booking(null, ref, passengers.findById(passengerId).orElseThrow(),
                flights.findById(flightId).orElseThrow(), seat, fareClass, baggage,
                new BigDecimal(price).setScale(2), status);
        LocalDateTime created = now.minusMinutes(minutesAgo);
        booking.restoreAudit(created, created);
        return bookings.insert(booking);
    }

    public Booking bookingByRef(String ref) {
        return bookings.findByBookingRef(ref).orElseThrow();
    }

    /** Собирает сервис броней с заданным генератором PNR поверх тех же репозиториев. */
    public BookingService bookingService(PnrGenerator pnrGenerator) {
        return new BookingService(bookings, passengers, flights, bookingValidator, new FareCalculator(),
                pnrGenerator, bookingMapper, Transactions.direct(), clock, EXPIRE_AFTER);
    }

    /**
     * Возвращает генератор, который выдаёт номера брони {@code refs} по порядку.
     *
     * <p>После последнего номера генератор повторяет его, поэтому {@code pnrSequence("AB12CD")}
     * всегда выдаёт один и тот же номер.</p>
     */
    public static PnrGenerator pnrSequence(String... refs) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int[] indexes = String.join("", refs).chars().map(alphabet::indexOf).toArray();
        int lastStart = indexes.length - refs[refs.length - 1].length();
        return new PnrGenerator(new RandomGenerator() {
            private int calls;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                int position = calls < indexes.length
                        ? calls
                        : lastStart + (calls - indexes.length) % (indexes.length - lastStart);
                calls++;
                return indexes[position];
            }
        });
    }

    private void passengers() {
        passenger("Иванов", "Иван", "Иванович", "1985-04-12", DocumentType.PASSPORT_RF, "4510123456", "ivanov");
        passenger("Петрова", "Анна", "Сергеевна", "1992-11-03", DocumentType.PASSPORT_RF, "4512654321", "petrova");
        passenger("Сидоров", "Пётр", "Николаевич", "1988-03-14", DocumentType.PASSPORT_RF, "4512889967", "sidorov");
        passenger("Кузнецова", "Мария", "Олеговна", "1979-07-22", DocumentType.INTERNATIONAL_PASSPORT,
                "750123456", "kuznetsova");
        passenger("Соколов", "Артём", "Дмитриевич", "2016-09-30", DocumentType.BIRTH_CERTIFICATE,
                "VIII-MU-123456", "sokolov");
        passenger("Новиков", "Денис", "Валерьевич", "1995-01-19", DocumentType.FOREIGN_DOCUMENT,
                "C01X00T47", "novikov");
    }

    private void bookings() {
        long day = 24 * 60;
        booking("AB12CD", 1, 1, "12A", FareClass.ECONOMY, false, "6400", BookingStatus.COMPLETED, 15 * day);
        booking("NR61WS", 2, 2, "12B", FareClass.COMFORT, true, "15780", BookingStatus.COMPLETED, 12 * day);
        booking("TY19GH", 3, 3, "3A", FareClass.BUSINESS, true, "16000", BookingStatus.CHECKED_IN, 5 * day);
        booking("ZX45TY", 4, 3, "14C", FareClass.COMFORT, true, "12740", BookingStatus.CHECKED_IN, 4 * day);
        booking("LM77PQ", 1, 4, "5F", FareClass.ECONOMY, true, "8900", BookingStatus.PAID, 3 * day);
        booking("RT58NB", 5, 5, "21D", FareClass.ECONOMY, false, "8300", BookingStatus.PAID, 3 * day);
        booking("HG23KL", 6, 5, "2A", FareClass.BUSINESS, true, "20750", BookingStatus.PAID, 2 * day);
        booking("VB07MD", 2, 6, "7A", FareClass.ECONOMY, false, "5200", BookingStatus.PAID, 2 * day);
        booking("QW34ER", 3, 6, "7B", FareClass.COMFORT, false, "8320", BookingStatus.PAID, day);
        booking("PL66CX", 4, 7, "30E", FareClass.ECONOMY, false, "4900", BookingStatus.CREATED, 5);
        booking("DF52JU", 5, 7, "30F", FareClass.ECONOMY, true, "7400", BookingStatus.CREATED, 3);
        booking("KJ90XZ", 6, 3, "18A", FareClass.ECONOMY, false, "6400", BookingStatus.CREATED, 1);
        booking("OS44BN", 1, 5, "9C", FareClass.COMFORT, false, "13280", BookingStatus.CANCELLED, 4 * day);
        booking("WE81YT", 2, 6, "12F", FareClass.ECONOMY, false, "5200", BookingStatus.EXPIRED, 3 * day);
    }
}
