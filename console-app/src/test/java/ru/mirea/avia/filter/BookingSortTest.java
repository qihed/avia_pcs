package ru.mirea.avia.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.support.TestContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет порядок демонстрационных броней при сортировках SO-01…SO-05. */
class BookingSortTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final List<Booking> all = ctx.bookings.findAll();

    @Test
    void sortsByIdByDefault() {
        List<String> refs = refs(BookingSort.BY_ID);

        assertThat(refs).hasSize(14);
        assertThat(refs.getFirst()).isEqualTo("AB12CD");
        assertThat(refs.getLast()).isEqualTo("WE81YT");
    }

    @Test
    void sortsByDepartureAscending() {
        List<String> refs = refs(BookingSort.BY_DEPARTURE);

        assertThat(refs).startsWith("AB12CD", "NR61WS", "TY19GH", "ZX45TY", "KJ90XZ", "LM77PQ");
        assertThat(refs).endsWith("PL66CX", "DF52JU");
    }

    @Test
    void sortsByPriceDescending() {
        List<String> refs = refs(BookingSort.BY_PRICE_DESC);

        assertThat(refs).startsWith("HG23KL", "TY19GH", "NR61WS", "OS44BN");
        assertThat(refs).endsWith("VB07MD", "WE81YT", "PL66CX");
    }

    @Test
    void sortsByPassengerLastName() {
        List<Booking> sorted = sorted(BookingSort.BY_LAST_NAME);

        assertThat(sorted.getFirst().getPassenger().getLastName()).isEqualTo("Иванов");
        assertThat(sorted.stream().map(Booking::getBookingRef).toList())
                .startsWith("AB12CD", "LM77PQ", "OS44BN", "ZX45TY", "PL66CX")
                .endsWith("RT58NB", "DF52JU");
    }

    @Test
    void sortsByCreationNewestFirst() {
        List<String> refs = refs(BookingSort.BY_CREATED_DESC);

        assertThat(refs).startsWith("KJ90XZ", "DF52JU", "PL66CX", "QW34ER", "HG23KL", "VB07MD");
        assertThat(refs).endsWith("NR61WS", "AB12CD");
    }

    @Test
    void sortsByStatusThenDeparture() {
        List<Booking> sorted = sorted(BookingSort.BY_STATUS_THEN_DEPARTURE);

        assertThat(sorted.getFirst().getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(sorted.getLast().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(sorted.stream().map(Booking::getBookingRef).toList()).containsExactly(
                "KJ90XZ", "PL66CX", "DF52JU",
                "LM77PQ", "RT58NB", "HG23KL", "VB07MD", "QW34ER",
                "TY19GH", "ZX45TY",
                "AB12CD", "NR61WS",
                "OS44BN",
                "WE81YT");
    }

    @Test
    void sortingKeepsSourceOrder() {
        sorted(BookingSort.BY_PRICE_DESC);

        assertThat(all).hasSize(14);
        assertThat(all.getFirst().getBookingRef()).isEqualTo("AB12CD");
    }

    private List<Booking> sorted(BookingSort sort) {
        return all.stream().sorted(sort.comparator()).toList();
    }

    private List<String> refs(BookingSort sort) {
        return sorted(sort).stream().map(Booking::getBookingRef).toList();
    }
}
