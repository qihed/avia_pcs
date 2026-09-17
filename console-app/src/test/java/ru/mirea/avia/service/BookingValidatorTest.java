package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.config.AppProperties.BookingRules;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.DuplicateBookingException;
import ru.mirea.avia.error.InvalidStatusTransitionException;
import ru.mirea.avia.error.SeatUnavailableException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.support.TestContext;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет правила бронирования валидатора и тексты его сообщений. */
class BookingValidatorTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final BookingValidator validator = ctx.bookingValidator;

    @Test
    void formsHoursAfterWordLessThan() {
        assertThat(BookingValidator.hoursText(1)).isEqualTo("1 часа");
        assertThat(BookingValidator.hoursText(2)).isEqualTo("2 часов");
        assertThat(BookingValidator.hoursText(5)).isEqualTo("5 часов");
        assertThat(BookingValidator.hoursText(11)).isEqualTo("11 часов");
        assertThat(BookingValidator.hoursText(21)).isEqualTo("21 часа");
        assertThat(BookingValidator.hoursText(24)).isEqualTo("24 часов");
        assertThat(BookingValidator.hoursText(101)).isEqualTo("101 часа");
        assertThat(BookingValidator.hoursText(111)).isEqualTo("111 часов");
    }

    @Test
    void usesConfiguredCancellationLimitInMessage() {
        BookingValidator oneHour = new BookingValidator(ctx.bookings, ctx.clock,
                new BookingRules(TestContext.SALE_CLOSE, Duration.ofHours(1), TestContext.EXPIRE_AFTER));
        Flight soon = ctx.flightAt("SU9301", "SVO", "LED", ctx.now.plusMinutes(30), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Booking booking = ctx.booking("QQ01QQ", 1, soon.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.PAID, 60);
        assertThatThrownBy(() -> oneHour.checkCanCancel(booking))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-304")
                .hasMessage("Отмена невозможна: до вылета осталось менее 1 часа");
    }

    @Test
    void acceptsFlightsOpenForSale() {
        Flight boundary = ctx.flightAt("SU9302", "SVO", "LED", ctx.now.plusMinutes(60), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        assertThatCode(() -> validator.checkOpenForSale(flight(3))).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkOpenForSale(flight(7))).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkOpenForSale(boundary)).doesNotThrowAnyException();
    }

    @Test
    void explainsThatCancelledFlightIsClosed() {
        assertThatThrownBy(() -> validator.checkOpenForSale(flight(8)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU1130 отменён и закрыт для продажи");
    }

    @Test
    void explainsThatArrivedFlightIsClosed() {
        assertThatThrownBy(() -> validator.checkOpenForSale(flight(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU1416 уже выполнен и закрыт для продажи");
    }

    @Test
    void explainsThatDepartedFlightIsClosed() {
        Flight departed = ctx.flightAt("SU9303", "SVO", "LED", ctx.now.plusDays(1), 80, 180, "5000",
                FlightStatus.DEPARTED);
        Flight overdue = ctx.flightAt("SU9304", "SVO", "LED", ctx.now.minusMinutes(5), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        assertThatThrownBy(() -> validator.checkOpenForSale(departed))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU9303 уже вылетел и закрыт для продажи");
        assertThatThrownBy(() -> validator.checkOpenForSale(overdue))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Рейс SU9304 уже вылетел, продажа закрыта");
    }

    @Test
    void closesSaleLessThanSixtyMinutesBeforeDeparture() {
        Flight scheduled = ctx.flightAt("SU9305", "SVO", "LED", ctx.now.plusMinutes(59), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Flight delayed = ctx.flightAt("SU9306", "SVO", "LED", ctx.now.plusMinutes(1), 80, 180, "5000",
                FlightStatus.DELAYED);
        assertThatThrownBy(() -> validator.checkOpenForSale(scheduled))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-300")
                .hasMessage("Продажа на рейс SU9305 закрыта: до вылета осталось менее 60 минут");
        assertThatThrownBy(() -> validator.checkOpenForSale(delayed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Продажа на рейс SU9306 закрыта: до вылета осталось менее 60 минут");
    }

    @Test
    void normalizesSeatNumber() {
        assertThat(validator.normalizeSeat("14c")).isEqualTo("14C");
        assertThat(validator.normalizeSeat(" 07a ")).isEqualTo("7A");
        assertThat(validator.normalizeSeat("99F")).isEqualTo("99F");
    }

    @Test
    void rejectsMalformedSeatNumber() {
        for (String seat : new String[] {"0A", "00A", "100A", "12G", "A12", "12", "", null}) {
            assertThatThrownBy(() -> validator.normalizeSeat(seat))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "E-103")
                    .hasMessage("Номер места должен иметь вид 12A (ряд и буква A–F)");
        }
    }

    @Test
    void normalizesBookingReference() {
        assertThat(validator.normalizeRef(" ab12cd ")).isEqualTo("AB12CD");
        for (String ref : new String[] {"AB12", "AB12CD7", "АВ12CD", "AB-12C", null}) {
            assertThatThrownBy(() -> validator.normalizeRef(ref))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "E-103")
                    .hasMessage("Номер брони должен состоять из 6 латинских букв и цифр, например AB12CD");
        }
    }

    @Test
    void checksSeatRowAgainstCabin() {
        assertThatCode(() -> validator.checkSeatInCabin(flight(3), "30F")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.checkSeatInCabin(flight(3), "31A"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("На рейсе SU1420 ряды с 1 по 30");
    }

    @Test
    void rejectsBookingAboveCapacity() {
        Flight small = ctx.flightAt("SU9307", "SVO", "LED", ctx.now.plusDays(2), 80, 1, "5000",
                FlightStatus.SCHEDULED);
        Booking only = ctx.booking("QQ02QQ", 1, small.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.PAID, 60);
        assertThatCode(() -> validator.checkCapacity(small, List.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.checkCapacity(small, List.of(only)))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301")
                .hasMessage("На рейсе SU9307 нет свободных мест");
    }

    @Test
    void ignoresOwnBookingWhenCheckingSeat() {
        List<Booking> active = ctx.bookings.findActiveByFlightId(3);
        long own = ctx.bookingByRef("ZX45TY").getId();
        assertThatThrownBy(() -> validator.checkSeatFree(flight(3), "14C", active, null))
                .isInstanceOf(SeatUnavailableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-301")
                .hasMessage("Место 14C на рейсе SU1420 уже занято");
        assertThatCode(() -> validator.checkSeatFree(flight(3), "14C", active, own)).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkSeatFree(flight(3), "20A", active, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSecondActiveBookingOfPassenger() {
        assertThatThrownBy(() -> validator.checkNoDuplicate(passenger(2), flight(6)))
                .isInstanceOf(DuplicateBookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-303")
                .hasMessage("У пассажира уже есть бронь VB07MD на рейс FV6021");
        assertThatCode(() -> validator.checkNoDuplicate(passenger(1), flight(3))).doesNotThrowAnyException();
    }

    @Test
    void permitsModificationOnlyBeforeCheckIn() {
        assertThatCode(() -> validator.checkCanModify(ctx.bookingByRef("RT58NB"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkCanModify(ctx.bookingByRef("KJ90XZ"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.checkCanModify(ctx.bookingByRef("AB12CD")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Изменить можно только бронь в статусе «Создано» или «Оплачено». "
                        + "Текущий статус брони AB12CD: «Перелёт выполнен»");
    }

    @Test
    void checksStatusTransitionMatrix() {
        assertThatThrownBy(() -> validator.checkStatusChange(ctx.bookingByRef("AB12CD"), BookingStatus.PAID))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-302")
                .hasMessage("Переход COMPLETED -> PAID недопустим. "
                        + "Бронь находится в финальном состоянии и не может быть изменена");
        assertThatThrownBy(() -> validator.checkStatusChange(ctx.bookingByRef("KJ90XZ"), BookingStatus.CREATED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход CREATED -> CREATED недопустим");
        assertThatThrownBy(() -> validator.checkStatusChange(ctx.bookingByRef("KJ90XZ"), BookingStatus.EXPIRED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Статус EXPIRED устанавливается системой автоматически "
                        + "для броней, не оплаченных вовремя (BR-09)");
        assertThatCode(() -> validator.checkStatusChange(ctx.bookingByRef("KJ90XZ"), BookingStatus.PAID))
                .doesNotThrowAnyException();
    }

    @Test
    void opensCheckInTwentyFourHoursBeforeDeparture() {
        assertThatThrownBy(() -> validator.checkStatusChange(ctx.bookingByRef("LM77PQ"), BookingStatus.CHECKED_IN))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Регистрация на рейс SU1421 открывается за 24 часа до вылета — с 16.09.2026 19:20");
        Flight boundary = ctx.flightAt("SU9308", "SVO", "LED", ctx.now.plusHours(24), 80, 180, "5000",
                FlightStatus.SCHEDULED);
        Booking booking = ctx.booking("QQ03QQ", 1, boundary.getId(), "1A", FareClass.ECONOMY, false, "5000",
                BookingStatus.PAID, 60);
        assertThatCode(() -> validator.checkStatusChange(booking, BookingStatus.CHECKED_IN))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsDeletionOnlyOfCancelledOrExpiredBooking() {
        assertThatCode(() -> validator.checkCanDelete(ctx.bookingByRef("OS44BN"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkCanDelete(ctx.bookingByRef("WE81YT"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.checkCanDelete(ctx.bookingByRef("KJ90XZ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Удалить можно только отменённую или просроченную бронь. "
                        + "Текущий статус брони KJ90XZ: «Создано, ожидает оплаты»");
    }

    private Flight flight(long id) {
        return ctx.flights.findById(id).orElseThrow();
    }

    private Passenger passenger(long id) {
        return ctx.passengers.findById(id).orElseThrow();
    }
}
