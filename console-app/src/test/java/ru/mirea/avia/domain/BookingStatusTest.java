package ru.mirea.avia.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет матрицу переходов статусов брони из п. 5.4.2 ТЗ (BR-07). */
class BookingStatusTest {
    // Строки — «из», столбцы — «в» в порядке CREATED, PAID, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED.
    private static final List<String> MATRIX = List.of(
            "-+--++",
            "--+-+-",
            "---+--",
            "------",
            "------",
            "------");

    @Test
    void canTransitionToMatchesSpecificationMatrix() {
        BookingStatus[] all = BookingStatus.values();
        assertThat(all).hasSize(MATRIX.size());
        for (int from = 0; from < all.length; from++) {
            for (int to = 0; to < all.length; to++) {
                boolean expected = MATRIX.get(from).charAt(to) == '+';
                assertThat(all[from].canTransitionTo(all[to]))
                        .as("%s -> %s", all[from], all[to])
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void listsAllowedTransitionsInDeclarationOrder() {
        assertThat(BookingStatus.CREATED.allowedTransitions())
                .containsExactly(BookingStatus.PAID, BookingStatus.CANCELLED, BookingStatus.EXPIRED);
        assertThat(BookingStatus.PAID.allowedTransitions())
                .containsExactly(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED);
        assertThat(BookingStatus.CHECKED_IN.allowedTransitions()).containsExactly(BookingStatus.COMPLETED);
    }

    @Test
    void finalStatesHaveNoTransitions() {
        assertThat(BookingStatus.COMPLETED.isFinal()).isTrue();
        assertThat(BookingStatus.CANCELLED.isFinal()).isTrue();
        assertThat(BookingStatus.EXPIRED.isFinal()).isTrue();
        assertThat(BookingStatus.COMPLETED.allowedTransitions()).isEmpty();
        assertThat(BookingStatus.CANCELLED.allowedTransitions()).isEmpty();
        assertThat(BookingStatus.EXPIRED.allowedTransitions()).isEmpty();
    }

    @Test
    void workingStatesAreNotFinal() {
        assertThat(BookingStatus.CREATED.isFinal()).isFalse();
        assertThat(BookingStatus.PAID.isFinal()).isFalse();
        assertThat(BookingStatus.CHECKED_IN.isFinal()).isFalse();
    }

    @Test
    void onlyCancelledAndExpiredReleaseSeat() {
        assertThat(BookingStatus.CREATED.occupiesSeat()).isTrue();
        assertThat(BookingStatus.PAID.occupiesSeat()).isTrue();
        assertThat(BookingStatus.CHECKED_IN.occupiesSeat()).isTrue();
        assertThat(BookingStatus.COMPLETED.occupiesSeat()).isTrue();
        assertThat(BookingStatus.CANCELLED.occupiesSeat()).isFalse();
        assertThat(BookingStatus.EXPIRED.occupiesSeat()).isFalse();
    }

    @Test
    void inProgressCoversCreatedPaidAndCheckedIn() {
        assertThat(BookingStatus.CREATED.isInProgress()).isTrue();
        assertThat(BookingStatus.PAID.isInProgress()).isTrue();
        assertThat(BookingStatus.CHECKED_IN.isInProgress()).isTrue();
        assertThat(BookingStatus.COMPLETED.isInProgress()).isFalse();
        assertThat(BookingStatus.CANCELLED.isInProgress()).isFalse();
        assertThat(BookingStatus.EXPIRED.isInProgress()).isFalse();
    }

    @Test
    void revenueCountsPaidCheckedInAndCompleted() {
        assertThat(BookingStatus.CREATED.isPaid()).isFalse();
        assertThat(BookingStatus.PAID.isPaid()).isTrue();
        assertThat(BookingStatus.CHECKED_IN.isPaid()).isTrue();
        assertThat(BookingStatus.COMPLETED.isPaid()).isTrue();
        assertThat(BookingStatus.CANCELLED.isPaid()).isFalse();
        assertThat(BookingStatus.EXPIRED.isPaid()).isFalse();
    }

    @Test
    void exposesRussianTitles() {
        assertThat(BookingStatus.CREATED.getTitle()).isEqualTo("Создано, ожидает оплаты");
        assertThat(BookingStatus.CREATED.getShortTitle()).isEqualTo("Создано");
        assertThat(BookingStatus.CHECKED_IN.getTitle()).isEqualTo("Регистрация пройдена");
        assertThat(BookingStatus.CHECKED_IN.getShortTitle()).isEqualTo("Регистрация");
        assertThat(BookingStatus.EXPIRED.getTitle()).isEqualTo("Бронь просрочена");
        assertThat(BookingStatus.EXPIRED.getShortTitle()).isEqualTo("Просрочено");
    }
}
