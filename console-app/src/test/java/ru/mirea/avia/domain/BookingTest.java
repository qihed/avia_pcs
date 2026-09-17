package ru.mirea.avia.domain;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.InvalidStatusTransitionException;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет жизненный цикл брони: статус CREATED, матрицу BR-07 и допустимые операции. */
class BookingTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final Passenger passenger = ctx.passengers.findById(1L).orElseThrow();
    private final Flight flight = ctx.flights.findById(3L).orElseThrow();

    @Test
    void newBookingStartsInCreatedStatus() {
        Booking booking = new Booking("ZZ11ZZ", passenger, flight, "1A", FareClass.ECONOMY, false,
                new BigDecimal("6400.00"));

        assertThat(booking.getId()).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(booking.isActive()).isTrue();
        assertThat(booking.getCreatedAt()).isNull();
    }

    @Test
    void onCreateSetsBothAuditTimestamps() {
        Booking booking = new Booking("ZZ11ZZ", passenger, flight, "1A", FareClass.ECONOMY, false,
                new BigDecimal("6400.00"));

        booking.onCreate(ctx.now);

        assertThat(booking.getCreatedAt()).isEqualTo(ctx.now);
        assertThat(booking.getUpdatedAt()).isEqualTo(ctx.now);
    }

    @Test
    void changeStatusFollowsMatrixAndUpdatesTimestamp() {
        Booking booking = ctx.bookingByRef("KJ90XZ");
        LocalDateTime created = booking.getCreatedAt();
        LocalDateTime paidAt = ctx.now.plusMinutes(1);
        LocalDateTime checkedInAt = ctx.now.plusMinutes(2);

        booking.changeStatus(BookingStatus.PAID, paidAt);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAID);
        assertThat(booking.getUpdatedAt()).isEqualTo(paidAt);

        booking.changeStatus(BookingStatus.CHECKED_IN, checkedInAt);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getUpdatedAt()).isEqualTo(checkedInAt);
        assertThat(booking.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void rejectsCompletedToPaidTransition() {
        Booking booking = ctx.bookingByRef("AB12CD");
        LocalDateTime updated = booking.getUpdatedAt();

        assertThatThrownBy(() -> booking.changeStatus(BookingStatus.PAID, ctx.now))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход COMPLETED -> PAID недопустим. "
                        + "Бронь находится в финальном состоянии и не может быть изменена")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_302);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    void rejectsSkippingPaymentWithoutFinalNote() {
        Booking booking = ctx.bookingByRef("KJ90XZ");

        assertThatThrownBy(() -> booking.changeStatus(BookingStatus.CHECKED_IN, ctx.now))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Переход CREATED -> CHECKED_IN недопустим");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CREATED);
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> new Booking("ZZ11ZZ", passenger, flight, "1A", FareClass.ECONOMY, false,
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Booking("ZZ11ZZ", passenger, flight, "1A", FareClass.ECONOMY, false,
                new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ctx.bookingByRef("KJ90XZ")
                .modify("2B", FareClass.BUSINESS, true, BigDecimal.ZERO, ctx.now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modifyReplacesSeatFareAndPrice() {
        Booking booking = ctx.bookingByRef("KJ90XZ");
        LocalDateTime changedAt = ctx.now.plusMinutes(5);

        booking.modify("2B", FareClass.BUSINESS, true, new BigDecimal("16000.00"), changedAt);

        assertThat(booking.getSeatNumber()).isEqualTo("2B");
        assertThat(booking.getFareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(booking.isBaggageIncluded()).isTrue();
        assertThat(booking.getPrice()).isEqualByComparingTo("16000");
        assertThat(booking.getUpdatedAt()).isEqualTo(changedAt);
    }

    @Test
    void onlyCreatedAndPaidBookingsCanBeCancelled() {
        assertThat(booking(BookingStatus.CREATED).canBeCancelled()).isTrue();
        assertThat(booking(BookingStatus.PAID).canBeCancelled()).isTrue();
        assertThat(booking(BookingStatus.CHECKED_IN).canBeCancelled()).isFalse();
        assertThat(booking(BookingStatus.COMPLETED).canBeCancelled()).isFalse();
        assertThat(booking(BookingStatus.CANCELLED).canBeCancelled()).isFalse();
        assertThat(booking(BookingStatus.EXPIRED).canBeCancelled()).isFalse();
    }

    @Test
    void onlyCreatedAndPaidBookingsCanBeModified() {
        assertThat(booking(BookingStatus.CREATED).canBeModified()).isTrue();
        assertThat(booking(BookingStatus.PAID).canBeModified()).isTrue();
        assertThat(booking(BookingStatus.CHECKED_IN).canBeModified()).isFalse();
        assertThat(booking(BookingStatus.COMPLETED).canBeModified()).isFalse();
        assertThat(booking(BookingStatus.CANCELLED).canBeModified()).isFalse();
        assertThat(booking(BookingStatus.EXPIRED).canBeModified()).isFalse();
    }

    @Test
    void onlyCancelledAndExpiredBookingsCanBeDeleted() {
        assertThat(booking(BookingStatus.CREATED).canBeDeleted()).isFalse();
        assertThat(booking(BookingStatus.PAID).canBeDeleted()).isFalse();
        assertThat(booking(BookingStatus.CHECKED_IN).canBeDeleted()).isFalse();
        assertThat(booking(BookingStatus.COMPLETED).canBeDeleted()).isFalse();
        assertThat(booking(BookingStatus.CANCELLED).canBeDeleted()).isTrue();
        assertThat(booking(BookingStatus.EXPIRED).canBeDeleted()).isTrue();
    }

    @Test
    void cancelledAndExpiredBookingsAreInactive() {
        assertThat(ctx.bookingByRef("OS44BN").isActive()).isFalse();
        assertThat(ctx.bookingByRef("WE81YT").isActive()).isFalse();
        assertThat(ctx.bookingByRef("AB12CD").isActive()).isTrue();
    }

    private Booking booking(BookingStatus status) {
        return new Booking(99L, "ZZ11ZZ", passenger, flight, "1A", FareClass.ECONOMY, false,
                new BigDecimal("6400.00"), status);
    }
}
