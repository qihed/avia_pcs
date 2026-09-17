package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.FlightDtos.FlightAvailability;
import ru.mirea.avia.dto.FlightDtos.FlightRequest;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.error.BusinessRuleException;
import ru.mirea.avia.error.EntityNotFoundException;
import ru.mirea.avia.error.InvalidStatusTransitionException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет ведение расписания: нормализацию рейса, уникальность, вместимость салона и статусы. */
class FlightServiceTest {
    private static final long SU1416_ARRIVED = 1;
    private static final long SU1420 = 3;
    private static final long SU1421 = 4;
    private static final long SU1108 = 5;
    private static final long SU1130_CANCELLED = 8;

    private final TestContext ctx = new TestContext().withDemoData();
    private final FlightService service = ctx.flightService;
    private final FlightValidator validator = service.validator();

    @Test
    void createsFlightWithNormalizedFields() {
        FlightResponse flight = service.create(request(" su 1500 ", "svo", "led", 180, "5000"));
        assertThat(flight.id()).isEqualTo(9);
        assertThat(flight.flightNumber()).isEqualTo("SU1500");
        assertThat(flight.airline()).isEqualTo("Аэрофлот");
        assertThat(flight.departureAirport()).isEqualTo("SVO");
        assertThat(flight.arrivalAirport()).isEqualTo("LED");
        assertThat(flight.route()).isEqualTo("SVO -> LED");
        assertThat(flight.aircraftType()).isEqualTo("Airbus A320");
        assertThat(flight.duration()).isEqualTo(Duration.ofHours(2));
        assertThat(flight.rowCount()).isEqualTo(30);
        assertThat(flight.basePrice()).isEqualTo(new BigDecimal("5000"));
        assertThat(flight.status()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(flight.allowedStatuses())
                .containsExactly(FlightStatus.DELAYED, FlightStatus.DEPARTED, FlightStatus.CANCELLED);
        assertThat(service.count()).isEqualTo(9);
        assertThat(ctx.flights.findById(9L).orElseThrow().getFlightNumber()).isEqualTo("SU1500");
    }

    @Test
    void limitsRowCountToTwoDigits() {
        assertThat(service.create(request("SU1501", "SVO", "LED", 600, "5000")).rowCount()).isEqualTo(99);
        assertThat(service.create(request("SU1502", "SVO", "LED", 1, "5000")).rowCount()).isEqualTo(1);
        assertThat(validator.normalizeFlightNumber("u62810")).isEqualTo("U62810");
        assertThat(validator.normalizeFlightNumber("fv 6021")).isEqualTo("FV6021");
    }

    @Test
    void rejectsInvalidRouteSeatsAndPrice() {
        assertThatThrownBy(() -> service.create(request("SU1500", "SVO", "svo", 180, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Аэропорт назначения должен отличаться от аэропорта вылета");
        assertThatThrownBy(() -> service.create(request("SU1500", "MOSCOW", "LED", 180, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Код аэропорта должен состоять из трёх латинских букв, например SVO");
        assertThatThrownBy(() -> service.create(request("SU1500", "SVO", "LED", 601, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Число мест должно быть от 1 до 600");
        assertThatThrownBy(() -> service.create(request("SU1500", "SVO", "LED", 0, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Число мест должно быть от 1 до 600");
        assertThatThrownBy(() -> service.create(request("SU1500", "SVO", "LED", 180, "1000000.01")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Базовый тариф должен быть больше нуля и не больше 1 000 000,00 руб.");
        assertThatThrownBy(() -> service.create(request("SU1500", "SVO", "LED", 180, "0")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
        assertThat(service.count()).isEqualTo(8);
    }

    @Test
    void rejectsInvalidNumberAndBlankFields() {
        assertThatThrownBy(() -> service.create(request("1234567", "SVO", "LED", 180, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Номер рейса должен иметь вид SU1416 (код авиакомпании и 1–4 цифры)");
        assertThatThrownBy(() -> service.create(request("  ", "SVO", "LED", 180, "5000")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Номер рейса не может быть пустым");
        LocalDateTime departure = ctx.now.plusDays(3);
        FlightRequest noAirline = new FlightRequest("SU1500", " ", "SVO", "LED", departure, departure.plusHours(2),
                "Airbus A320", 180, new BigDecimal("5000"));
        assertThatThrownBy(() -> service.create(noAirline))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Поле «Авиакомпания» не может быть пустым");
        FlightRequest noPrice = new FlightRequest("SU1500", "Аэрофлот", "SVO", "LED", departure,
                departure.plusHours(2), "Airbus A320", 180, null);
        assertThatThrownBy(() -> service.create(noPrice))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Базовый тариф не может быть пустым");
    }

    @Test
    void rejectsPastDepartureAndInvertedSchedule() {
        FlightRequest past = new FlightRequest("SU1501", "Аэрофлот", "SVO", "LED", ctx.now.minusHours(1),
                ctx.now.plusHours(1), "Airbus A320", 180, new BigDecimal("5000"));
        assertThatThrownBy(() -> service.create(past))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102")
                .hasMessage("Время вылета нового рейса должно быть в будущем");
        FlightRequest inverted = new FlightRequest("SU1501", "Аэрофлот", "SVO", "LED", ctx.now.plusDays(1),
                ctx.now.plusDays(1).minusHours(1), "Airbus A320", 180, new BigDecimal("5000"));
        assertThatThrownBy(() -> service.create(inverted))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102")
                .hasMessage("Время прилёта должно быть позже времени вылета");
        FlightRequest noSchedule = new FlightRequest("SU1501", "Аэрофлот", "SVO", "LED", null, null,
                "Airbus A320", 180, new BigDecimal("5000"));
        assertThatThrownBy(() -> service.create(noSchedule))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Время вылета и прилёта должно быть заполнено");
        assertThatThrownBy(() -> service.checkFutureDeparture(ctx.now))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102");
        assertThat(service.checkFutureDeparture(ctx.now.plusMinutes(1))).isEqualTo(ctx.now.plusMinutes(1));
    }

    @Test
    void rejectsDuplicateNumberAndDeparture() {
        FlightRequest copy = service.get(SU1420).flight().toRequest();
        assertThatThrownBy(() -> service.create(copy))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307")
                .hasMessage("Рейс SU1420 с таким временем вылета уже есть в расписании (ID 3)");
        assertThatThrownBy(() -> service.update(SU1421, copy))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307");
        assertThatThrownBy(() -> service.checkNumberFree("SU1420", copy.departureTime(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-307");
        assertThatCode(() -> service.checkNumberFree("SU1420", copy.departureTime(), SU1420))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.checkNumberFree("SU1420", copy.departureTime().plusDays(1), null))
                .doesNotThrowAnyException();
        assertThat(service.count()).isEqualTo(8);
    }

    @Test
    void rejectsSeatsBelowActiveBookings() {
        FlightRequest request = withSeats(service.get(SU1420).flight().toRequest(), 2);
        assertThatThrownBy(() -> service.update(SU1420, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Нельзя уменьшить число мест до 2: на рейс оформлено активных броней: 3");
        assertThat(ctx.flights.findById(SU1420).orElseThrow().getTotalSeats()).isEqualTo(180);
    }

    @Test
    void rejectsShrinkingCabinBelowSoldSeatRow() {
        FlightRequest request = withSeats(service.get(SU1420).flight().toRequest(), 90);
        assertThatThrownBy(() -> service.update(SU1420, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Нельзя уменьшить число мест до 90: место 18A (бронь KJ90XZ) "
                        + "окажется за пределами салона");
        assertThat(ctx.flights.findById(SU1420).orElseThrow().getTotalSeats()).isEqualTo(180);
    }

    @Test
    void updatesFlightWhenSoldSeatsStillFit() {
        FlightRequest current = service.get(SU1420).flight().toRequest();
        FlightRequest changed = new FlightRequest(current.flightNumber(), "Россия", current.departureAirport(),
                current.arrivalAirport(), current.departureTime(), current.arrivalTime(), "Boeing 737-800",
                108, new BigDecimal("7000.00"));
        FlightResponse updated = service.update(SU1420, changed);
        assertThat(updated.totalSeats()).isEqualTo(108);
        assertThat(updated.rowCount()).isEqualTo(18);
        assertThat(updated.airline()).isEqualTo("Россия");
        assertThat(updated.aircraftType()).isEqualTo("Boeing 737-800");
        assertThat(updated.basePrice()).isEqualTo(new BigDecimal("7000.00"));
        assertThat(service.get(SU1420).freeSeats()).isEqualTo(105);
        assertThatThrownBy(() -> service.update(99, changed))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202");
    }

    @Test
    void refusesToCancelFlightWithCheckedInPassengers() {
        assertThatThrownBy(() -> service.changeStatus(SU1420, FlightStatus.CANCELLED))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-306")
                .hasMessage("Нельзя отменить рейс SU1420: на него уже зарегистрированы пассажиры (2)");
        assertThat(ctx.flights.findById(SU1420).orElseThrow().getStatus()).isEqualTo(FlightStatus.SCHEDULED);

        FlightResponse cancelled = service.changeStatus(SU1108, FlightStatus.CANCELLED);
        assertThat(cancelled.status()).isEqualTo(FlightStatus.CANCELLED);
        assertThat(cancelled.allowedStatuses()).isEmpty();
    }

    @Test
    void changesStatusByTransitionMatrix() {
        FlightResponse delayed = service.changeStatus(SU1420, FlightStatus.DELAYED);
        assertThat(delayed.status()).isEqualTo(FlightStatus.DELAYED);
        assertThat(delayed.allowedStatuses())
                .containsExactly(FlightStatus.SCHEDULED, FlightStatus.DEPARTED, FlightStatus.CANCELLED);
        assertThat(service.changeStatus(SU1420, FlightStatus.SCHEDULED).status()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(service.changeStatus(SU1420, FlightStatus.DEPARTED).allowedStatuses())
                .containsExactly(FlightStatus.ARRIVED);
        assertThat(service.changeStatus(SU1420, FlightStatus.ARRIVED).allowedStatuses()).isEmpty();

        assertThatThrownBy(() -> service.changeStatus(SU1130_CANCELLED, FlightStatus.SCHEDULED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-302")
                .hasMessage("Переход CANCELLED -> SCHEDULED недопустим. "
                        + "Рейс находится в финальном состоянии и не может быть изменён");
        assertThatThrownBy(() -> service.changeStatus(SU1421, FlightStatus.ARRIVED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход SCHEDULED -> ARRIVED недопустим");
        assertThatThrownBy(() -> service.changeStatus(99, FlightStatus.DELAYED))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Рейс с ID 99 не найден");
    }

    @Test
    void listsOnlyFlightsOpenForSaleWithFreeSeats() {
        Flight full = ctx.flightAt("SU9201", "SVO", "LED", ctx.now.plusDays(2), 80, 1, "5000",
                FlightStatus.SCHEDULED);
        ctx.booking("FULL01", 1, full.getId(), "1A", FareClass.ECONOMY, false, "5000", BookingStatus.PAID, 60);
        ctx.flightAt("SU9202", "SVO", "LED", ctx.now.plusMinutes(30), 80, 180, "5000", FlightStatus.SCHEDULED);

        assertThat(service.listOpenForSale())
                .allMatch(FlightAvailability::openForSale)
                .extracting(availability -> availability.flight().flightNumber())
                .containsExactly("SU1420", "SU1421", "SU1108", "FV6021", "U62810");
        assertThat(service.list()).hasSize(10);
        assertThat(service.get(full.getId()).freeSeats()).isZero();
        assertThat(service.get(full.getId()).openForSale()).isTrue();
    }

    @Test
    void returnsOccupancyOfFlight() {
        FlightAvailability su1420 = service.get(SU1420);
        assertThat(su1420.occupiedSeats()).isEqualTo(3);
        assertThat(su1420.freeSeats()).isEqualTo(177);
        assertThat(su1420.openForSale()).isTrue();
        assertThat(su1420.flight().rowCount()).isEqualTo(30);

        FlightAvailability su1108 = service.get(SU1108);
        assertThat(su1108.occupiedSeats()).isEqualTo(2);
        assertThat(su1108.freeSeats()).isEqualTo(126);

        FlightAvailability arrived = service.get(SU1416_ARRIVED);
        assertThat(arrived.occupiedSeats()).isEqualTo(1);
        assertThat(arrived.openForSale()).isFalse();

        assertThatThrownBy(() -> service.get(99))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202")
                .hasMessage("Рейс с ID 99 не найден");
    }

    @Test
    void deletesOnlyFlightWithoutBookings() {
        assertThatThrownBy(() -> service.checkCanDelete(SU1420))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-305")
                .hasMessage("Нельзя удалить рейс SU1420: на него оформлены бронирования (3)");
        assertThatThrownBy(() -> service.delete(SU1108))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Нельзя удалить рейс SU1108: на него оформлены бронирования (3)");
        assertThatCode(() -> service.checkCanDelete(SU1130_CANCELLED)).doesNotThrowAnyException();

        service.delete(SU1130_CANCELLED);
        assertThat(service.count()).isEqualTo(7);
        assertThatThrownBy(() -> service.get(SU1130_CANCELLED))
                .isInstanceOf(EntityNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-202");
    }

    private FlightRequest request(String number, String from, String to, int seats, String price) {
        LocalDateTime departure = ctx.now.plusDays(3);
        return new FlightRequest(number, "  Аэрофлот ", from, to, departure, departure.plusHours(2),
                "Airbus   A320", seats, new BigDecimal(price));
    }

    private static FlightRequest withSeats(FlightRequest request, int totalSeats) {
        return new FlightRequest(request.flightNumber(), request.airline(), request.departureAirport(),
                request.arrivalAirport(), request.departureTime(), request.arrivalTime(), request.aircraftType(),
                totalSeats, request.basePrice());
    }
}
