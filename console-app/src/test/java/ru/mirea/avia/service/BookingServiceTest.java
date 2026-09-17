package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.BookingDtos.BookingChangeRequest;
import ru.mirea.avia.dto.BookingDtos.BookingRequest;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.DuplicateBookingException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.InvalidStatusTransitionException;
import ru.mirea.avia.error.SeatUnavailableException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет бизнес-правила BR-01…BR-10 и тест-кейсы TC-04…TC-12 на репозиториях в памяти. */
class BookingServiceTest {
    private static final long SU1420 = 3;
    private static final long SU1108 = 5;
    private static final long FV6021 = 6;
    private static final long U62810 = 7;
    private static final long SU1130_CANCELLED = 8;

    private final TestContext ctx = new TestContext().withDemoData();
    private final BookingService service = ctx.bookingService;

    @Test
    void createsBookingInCreatedStatusWithReferenceAndPrice() {
        BookingResponse booking = service.create(new BookingRequest(1, SU1420, FareClass.COMFORT, "12c", true));
        assertThat(booking.status()).isEqualTo(BookingStatus.CREATED);
        assertThat(booking.bookingRef()).matches("[A-Z0-9]{6}");
        assertThat(booking.seatNumber()).isEqualTo("12C");
        assertThat(booking.price()).isEqualTo(new BigDecimal("12740.00"));
        assertThat(booking.createdAt()).isEqualTo(ctx.now);
        assertThat(booking.allowedStatuses()).containsExactly(BookingStatus.PAID, BookingStatus.CANCELLED);
        assertThat(booking.passenger().shortName()).isEqualTo("Иванов И. И.");
        assertThat(booking.flight().flightNumber()).isEqualTo("SU1420");
        assertThat(ctx.bookings.count()).isEqualTo(15);
        assertThat(ctx.bookingByRef(booking.bookingRef()).getId()).isEqualTo(booking.id());
    }

    @Test
    void businessClassBookingAlwaysIncludesBaggage() {
        BookingResponse booking = service.create(new BookingRequest(1, SU1420, FareClass.BUSINESS, "2C", false));
        assertThat(booking.baggageIncluded()).isTrue();
        assertThat(booking.price()).isEqualTo(new BigDecimal("16000.00"));
    }

    @Test
    void rejectsTakenSeat() {
        assertThatThrownBy(() -> service.create(new BookingRequest(1, SU1420, FareClass.ECONOMY, "14C", false)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301")
                .hasMessage("Место 14C на рейсе SU1420 уже занято");
        assertThat(ctx.bookings.count()).isEqualTo(14);
    }

    @Test
    void rejectsSecondActiveBookingOfPassengerOnFlight() {
        assertThatThrownBy(() -> service.create(new BookingRequest(3, SU1420, FareClass.ECONOMY, "20A", false)))
                .isInstanceOf(DuplicateBookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-303")
                .hasMessage("У пассажира уже есть бронь TY19GH на рейс SU1420");
        assertThat(ctx.bookings.count()).isEqualTo(14);
    }

    @Test
    void expiredBookingDoesNotBlockNewBookingOfSamePassenger() {
        ctx.bookings.deleteById(ctx.bookingByRef("VB07MD").getId());
        BookingResponse booking = service.create(new BookingRequest(2, FV6021, FareClass.ECONOMY, "12F", false));
        assertThat(booking.status()).isEqualTo(BookingStatus.CREATED);
        assertThat(booking.seatNumber()).isEqualTo("12F");
        assertThat(ctx.bookingByRef("WE81YT").getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void rejectsBookingOnCancelledFlight() {
        assertThatThrownBy(() -> service.create(
                new BookingRequest(1, SU1130_CANCELLED, FareClass.ECONOMY, "1A", false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU1130 отменён и закрыт для продажи");
    }

    @Test
    void closesSaleSixtyMinutesBeforeDeparture() {
        Flight soon = ctx.flightAt("SU9001", "SVO", "LED", ctx.now.plusMinutes(59), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Flight later = ctx.flightAt("SU9002", "SVO", "LED", ctx.now.plusMinutes(60), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        assertThatThrownBy(() -> service.create(new BookingRequest(1, soon.getId(), FareClass.ECONOMY, "1A", false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Продажа на рейс SU9001 закрыта: до вылета осталось менее 60 минут");
        BookingResponse booking = service.create(new BookingRequest(1, later.getId(), FareClass.ECONOMY, "1A", false));
        assertThat(booking.status()).isEqualTo(BookingStatus.CREATED);
    }

    @Test
    void rejectsBookingWhenNoSeatsLeft() {
        Flight small = ctx.flightAt("SU9003", "SVO", "LED", ctx.now.plusDays(2), 80, 1, "5000",
                FlightStatus.SCHEDULED);
        service.create(new BookingRequest(1, small.getId(), FareClass.ECONOMY, "1A", false));
        assertThatThrownBy(() -> service.create(new BookingRequest(2, small.getId(), FareClass.ECONOMY, "1B", false)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301")
                .hasMessage("На рейсе SU9003 нет свободных мест");
    }

    @Test
    void requiresExistingPassengerAndFlight() {
        assertThatThrownBy(() -> service.create(new BookingRequest(42, SU1420, FareClass.ECONOMY, "1A", false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202")
                .hasMessage("Пассажир с ID 42 не найден");
        assertThatThrownBy(() -> service.create(new BookingRequest(1, 99, FareClass.ECONOMY, "1A", false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202")
                .hasMessage("Рейс с ID 99 не найден");
    }

    @Test
    void validatesSeatFormatAndRow() {
        assertThatThrownBy(() -> service.checkSeatAvailable(SU1420, "12G"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Номер места должен иметь вид 12A (ряд и буква A–F)");
        assertThatThrownBy(() -> service.checkSeatAvailable(SU1420, "31A"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("На рейсе SU1420 ряды с 1 по 30");
        assertThatThrownBy(() -> service.checkSeatAvailable(SU1420, "14c"))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301");
        assertThat(service.checkSeatAvailable(SU1108, "9c")).isEqualTo("9C");
        assertThat(service.normalizeSeat(" 07a ")).isEqualTo("7A");
    }

    @Test
    void checksFlightAvailabilityForPassenger() {
        assertThatCode(() -> service.checkFlightAvailable(1, SU1420)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkFlightAvailable(3, SU1420))
                .isInstanceOf(DuplicateBookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-303");
        assertThatThrownBy(() -> service.checkFlightAvailable(1, SU1130_CANCELLED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300");
        assertThatThrownBy(() -> service.checkFlightAvailable(42, SU1420))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202");
    }

    @Test
    void quotesPriceBeforeSaving() {
        assertThat(service.quote(SU1420, FareClass.COMFORT, true).total()).isEqualTo(new BigDecimal("12740.00"));
        assertThat(service.quote(SU1108, FareClass.BUSINESS, false).total()).isEqualTo(new BigDecimal("20750.00"));
        assertThatThrownBy(() -> service.quote(99, FareClass.ECONOMY, false))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Рейс с ID 99 не найден");
        assertThat(ctx.bookings.count()).isEqualTo(14);
    }

    @Test
    void generatesNextReferenceWhenFirstOneIsTaken() {
        BookingService retrying = ctx.bookingService(TestContext.pnrSequence("AB12CD", "QA77ZZ"));
        BookingResponse booking = retrying.create(new BookingRequest(1, SU1420, FareClass.ECONOMY, "20A", false));
        assertThat(booking.bookingRef()).isEqualTo("QA77ZZ");
    }

    @Test
    void failsWhenUniqueReferenceCannotBeGenerated() {
        BookingService stuck = ctx.bookingService(TestContext.pnrSequence("AB12CD"));
        assertThatThrownBy(() -> stuck.create(new BookingRequest(1, SU1420, FareClass.ECONOMY, "20A", false)))
                .isInstanceOf(DataAccessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-502")
                .hasMessage("Failed to generate unique booking reference");
        assertThat(ctx.bookings.count()).isEqualTo(14);
    }

    @Test
    void rejectsTransitionFromCompletedToPaid() {
        long id = ctx.bookingByRef("AB12CD").getId();
        assertThatThrownBy(() -> service.changeStatus(id, BookingStatus.PAID))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-302")
                .hasMessage("Переход COMPLETED -> PAID недопустим. "
                        + "Бронь находится в финальном состоянии и не может быть изменена");
        assertThat(ctx.bookingByRef("AB12CD").getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void paysAndChecksInBookingBeforeDeparture() {
        long id = ctx.bookingByRef("KJ90XZ").getId();
        assertThat(service.get(id).allowedStatuses()).containsExactly(BookingStatus.PAID, BookingStatus.CANCELLED);

        BookingResponse paid = service.changeStatus(id, BookingStatus.PAID);
        assertThat(paid.status()).isEqualTo(BookingStatus.PAID);
        assertThat(paid.allowedStatuses()).containsExactly(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED);
        assertThat(paid.updatedAt()).isEqualTo(ctx.now);

        BookingResponse checkedIn = service.changeStatus(id, BookingStatus.CHECKED_IN);
        assertThat(checkedIn.status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(checkedIn.allowedStatuses()).containsExactly(BookingStatus.COMPLETED);

        assertThatThrownBy(() -> service.changeStatus(id, BookingStatus.COMPLETED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Перелёт ещё не выполнен: прилёт рейса SU1420 ожидается 17.09.2026 10:00");
        assertThat(ctx.bookingByRef("KJ90XZ").getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void completesCheckedInBookingAfterArrival() {
        Flight arrived = ctx.flightAt("SU9005", "SVO", "LED", ctx.now.minusHours(3), 80, 180, "5000",
                FlightStatus.ARRIVED);
        Booking booking = ctx.booking("QQ55QQ", 1, arrived.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.CHECKED_IN, 24 * 60);
        BookingResponse completed = service.changeStatus(booking.getId(), BookingStatus.COMPLETED);
        assertThat(completed.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(completed.allowedStatuses()).isEmpty();
    }

    @Test
    void opensCheckInOnlyTwentyFourHoursBeforeDeparture() {
        long id = ctx.bookingByRef("RT58NB").getId();
        assertThatThrownBy(() -> service.changeStatus(id, BookingStatus.CHECKED_IN))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Регистрация на рейс SU1108 открывается за 24 часа до вылета — с 22.09.2026 19:05");
        assertThat(ctx.bookingByRef("RT58NB").getStatus()).isEqualTo(BookingStatus.PAID);
    }

    @Test
    void rejectsManualExpiredStatus() {
        long created = ctx.bookingByRef("KJ90XZ").getId();
        long paid = ctx.bookingByRef("RT58NB").getId();
        assertThatThrownBy(() -> service.changeStatus(created, BookingStatus.EXPIRED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-302")
                .hasMessageContaining("устанавливается системой автоматически");
        assertThatThrownBy(() -> service.changeStatus(paid, BookingStatus.EXPIRED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход PAID -> EXPIRED недопустим");
        assertThat(ctx.bookingByRef("KJ90XZ").getStatus()).isEqualTo(BookingStatus.CREATED);
    }

    @Test
    void rejectsCancellationLessThanTwoHoursBeforeDeparture() {
        Flight soon = ctx.flightAt("SU9004", "SVO", "LED", ctx.now.plusMinutes(30), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Booking booking = ctx.booking("QQ11QQ", 1, soon.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.PAID, 24 * 60);
        assertThatThrownBy(() -> service.checkCanCancel(booking.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-304");
        assertThatThrownBy(() -> service.cancel(booking.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-304")
                .hasMessage("Отмена невозможна: до вылета осталось менее 2 часов");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
    }

    @Test
    void permitsCancellationExactlyTwoHoursBeforeDeparture() {
        Flight flight = ctx.flightAt("SU9007", "SVO", "LED", ctx.now.plusHours(2), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Booking booking = ctx.booking("QQ77QQ", 1, flight.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.PAID, 24 * 60);
        assertThat(service.cancel(booking.getId()).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelledBookingReleasesSeat() {
        long checkedIn = ctx.bookingByRef("ZX45TY").getId();
        assertThatThrownBy(() -> service.cancel(checkedIn))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-302")
                .hasMessage("Переход CHECKED_IN -> CANCELLED недопустим");

        BookingResponse cancelled = service.cancel(ctx.bookingByRef("RT58NB").getId());
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.allowedStatuses()).isEmpty();

        BookingResponse again = service.create(new BookingRequest(2, SU1108, FareClass.ECONOMY, "21D", false));
        assertThat(again.seatNumber()).isEqualTo("21D");
        assertThat(again.status()).isEqualTo(BookingStatus.CREATED);
    }

    @Test
    void explainsWhyBookingCannotBeCancelled() {
        long checkedIn = ctx.bookingByRef("ZX45TY").getId();
        long completed = ctx.bookingByRef("AB12CD").getId();
        assertThatThrownBy(() -> service.checkCanCancel(checkedIn))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход CHECKED_IN -> CANCELLED недопустим. "
                        + "Отменить можно только бронь в статусе «Создано» или «Оплачено»");
        assertThatThrownBy(() -> service.checkCanCancel(completed))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход COMPLETED -> CANCELLED недопустим. "
                        + "Бронь находится в финальном состоянии и не может быть изменена");
        assertThatCode(() -> service.checkCanCancel(ctx.bookingByRef("RT58NB").getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void bookingOnCancelledFlightCanBeCancelledAnytime() {
        Flight departedAndCancelled = ctx.flightAt("SU9006", "SVO", "LED", ctx.now.minusHours(1), 80, 180, "5000",
                FlightStatus.CANCELLED);
        Booking upcoming = ctx.booking("QQ22QQ", 3, SU1130_CANCELLED, "1A", FareClass.ECONOMY, false, "9200",
                BookingStatus.PAID, 60);
        Booking past = ctx.booking("QQ66QQ", 3, departedAndCancelled.getId(), "1A", FareClass.ECONOMY, false,
                "5000", BookingStatus.CREATED, 10);
        assertThat(service.cancel(upcoming.getId()).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(service.cancel(past.getId()).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void departedFlightBlocksCheckInAndCancellation() {
        long id = ctx.bookingByRef("HG23KL").getId();
        ctx.flightService.changeStatus(SU1108, FlightStatus.DEPARTED);
        assertThatThrownBy(() -> service.cancel(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-304")
                .hasMessage("Отмена невозможна: рейс SU1108 уже вылетел");
        assertThatThrownBy(() -> service.changeStatus(id, BookingStatus.CHECKED_IN))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Регистрация невозможна: рейс SU1108 уже вылетел");
        assertThat(ctx.bookingByRef("HG23KL").getStatus()).isEqualTo(BookingStatus.PAID);
    }

    @Test
    void departedFlightBlocksPayment() {
        long id = ctx.bookingByRef("KJ90XZ").getId();
        ctx.flightService.changeStatus(SU1420, FlightStatus.DEPARTED);
        assertThatThrownBy(() -> service.changeStatus(id, BookingStatus.PAID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Оплата невозможна: рейс SU1420 уже вылетел");
        assertThat(ctx.bookingByRef("KJ90XZ").getStatus()).isEqualTo(BookingStatus.CREATED);
    }

    @Test
    void cancelledFlightBlocksPayment() {
        Booking booking = ctx.booking("QQ33QQ", 3, SU1130_CANCELLED, "2A", FareClass.ECONOMY, false, "9200",
                BookingStatus.CREATED, 1);
        assertThatThrownBy(() -> service.changeStatus(booking.getId(), BookingStatus.PAID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Оплата невозможна: рейс SU1130 отменён");
    }

    @Test
    void deletesOnlyCancelledOrExpiredBookings() {
        long paid = ctx.bookingByRef("LM77PQ").getId();
        assertThatThrownBy(() -> service.delete(paid))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Удалить можно только отменённую или просроченную бронь. "
                        + "Текущий статус брони LM77PQ: «Оплачено»");
        assertThatThrownBy(() -> service.checkCanDelete(paid))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305");
        assertThat(ctx.bookings.count()).isEqualTo(14);

        long cancelled = ctx.bookingByRef("OS44BN").getId();
        assertThatCode(() -> service.checkCanDelete(cancelled)).doesNotThrowAnyException();
        service.delete(cancelled);
        service.delete(ctx.bookingByRef("WE81YT").getId());
        assertThat(ctx.bookings.count()).isEqualTo(12);
        assertThat(ctx.bookings.findByBookingRef("OS44BN")).isEmpty();
        assertThatThrownBy(() -> service.delete(cancelled))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-201");
    }

    @Test
    void bookingsBlockDeletingPassengerAndFlight() {
        assertThatThrownBy(() -> ctx.passengerService.delete(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Нельзя удалить пассажира Иванов И. И.: у него есть бронирования (3)");
        assertThatThrownBy(() -> ctx.flightService.delete(SU1420))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Нельзя удалить рейс SU1420: на него оформлены бронирования (3)");
        assertThat(ctx.passengers.count()).isEqualTo(6);

        ctx.flightService.delete(SU1130_CANCELLED);
        assertThat(ctx.flights.count()).isEqualTo(7);
    }

    @Test
    void expiresBookingsUnpaidForMoreThanThirtyMinutes() {
        ctx.booking("OLD001", 1, FV6021, "1A", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 31);
        ctx.booking("EDGE30", 4, FV6021, "1B", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 30);
        ctx.booking("NEW001", 3, U62810, "1A", FareClass.ECONOMY, false, "4900", BookingStatus.CREATED, 29);
        assertThat(service.expireAfter()).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.expireOutdated()).isEqualTo(1);
        assertThat(ctx.bookingByRef("OLD001").getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(ctx.bookingByRef("OLD001").getUpdatedAt()).isEqualTo(ctx.now);
        assertThat(ctx.bookingByRef("EDGE30").getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(ctx.bookingByRef("NEW001").getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(ctx.bookingByRef("KJ90XZ").getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(service.expireOutdated()).isZero();
    }

    @Test
    void findExpiresOutdatedBookingsFirst() {
        ctx.booking("OLD002", 1, FV6021, "1A", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 45);
        assertThat(service.find("OLD002").status()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void listAndActiveCounterExpireOutdatedBookingsFirst() {
        ctx.booking("OLD003", 1, FV6021, "1A", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 45);
        assertThat(service.countActive()).isEqualTo(12);

        ctx.booking("OLD004", 4, FV6021, "1B", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 45);
        assertThat(service.list())
                .hasSize(16)
                .filteredOn(booking -> booking.bookingRef().equals("OLD004"))
                .extracting(BookingResponse::status)
                .containsExactly(BookingStatus.EXPIRED);
    }

    @Test
    void createReleasesSeatsOfOutdatedBookingsFirst() {
        ctx.booking("OLD005", 4, FV6021, "1A", FareClass.ECONOMY, false, "5200", BookingStatus.CREATED, 31);
        BookingResponse booking = service.create(new BookingRequest(1, FV6021, FareClass.ECONOMY, "1A", false));
        assertThat(booking.seatNumber()).isEqualTo("1A");
        assertThat(ctx.bookingByRef("OLD005").getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void updateRecalculatesPriceAndChecksSeat() {
        long id = ctx.bookingByRef("KJ90XZ").getId();
        assertThatThrownBy(() -> service.update(id, new BookingChangeRequest("3A", FareClass.ECONOMY, false)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301")
                .hasMessage("Место 3A на рейсе SU1420 уже занято");

        BookingResponse business = service.update(id, new BookingChangeRequest("2b", FareClass.BUSINESS, false));
        assertThat(business.seatNumber()).isEqualTo("2B");
        assertThat(business.fareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(business.baggageIncluded()).isTrue();
        assertThat(business.price()).isEqualTo(new BigDecimal("16000.00"));
        assertThat(business.updatedAt()).isEqualTo(ctx.now);

        BookingResponse comfort = service.update(id, new BookingChangeRequest("2B", FareClass.COMFORT, true));
        assertThat(comfort.price()).isEqualTo(new BigDecimal("12740.00"));
        assertThat(ctx.bookingByRef("KJ90XZ").getPrice()).isEqualTo(new BigDecimal("12740.00"));
        assertThat(ctx.bookingByRef("KJ90XZ").getSeatNumber()).isEqualTo("2B");
    }

    @Test
    void forbidsUpdateAfterCheckIn() {
        long id = ctx.bookingByRef("TY19GH").getId();
        assertThatThrownBy(() -> service.update(id, new BookingChangeRequest("4A", FareClass.BUSINESS, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Изменить можно только бронь в статусе «Создано» или «Оплачено». "
                        + "Текущий статус брони TY19GH: «Регистрация пройдена»");
        assertThatThrownBy(() -> service.checkCanModify(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306");
        assertThat(ctx.bookingByRef("TY19GH").getSeatNumber()).isEqualTo("3A");
    }

    @Test
    void forbidsUpdateWhenFlightIsClosedForSale() {
        Booking booking = ctx.booking("QQ44QQ", 1, SU1130_CANCELLED, "1A", FareClass.ECONOMY, false, "9200",
                BookingStatus.PAID, 60);
        assertThatThrownBy(() -> service.update(booking.getId(),
                new BookingChangeRequest("2A", FareClass.ECONOMY, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU1130 отменён и закрыт для продажи");
        assertThatCode(() -> service.checkCanModify(ctx.bookingByRef("RT58NB").getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void findsBookingByIdOrReference() {
        assertThat(service.find("1").bookingRef()).isEqualTo("AB12CD");
        assertThat(service.find(" ab12cd ").bookingRef()).isEqualTo("AB12CD");
        assertThat(service.get(1).bookingRef()).isEqualTo("AB12CD");
        assertThatThrownBy(() -> service.find("137"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-201")
                .hasMessage("Бронирование с ID 137 не найдено");
        assertThatThrownBy(() -> service.get(137))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-201");
        assertThatThrownBy(() -> service.find("ZZ99ZZ"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-201")
                .hasMessage("Бронирование с номером ZZ99ZZ не найдено");
        assertThatThrownBy(() -> service.find("AB-12"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Номер брони должен состоять из 6 латинских букв и цифр, например AB12CD");
    }

    @Test
    void countersMatchMainScreen() {
        assertThat(ctx.passengerService.count()).isEqualTo(6);
        assertThat(ctx.flightService.count()).isEqualTo(8);
        assertThat(service.countActive()).isEqualTo(12);
        assertThat(service.count()).isEqualTo(14);
    }
}
