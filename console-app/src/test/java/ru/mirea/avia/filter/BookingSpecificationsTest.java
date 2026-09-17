package ru.mirea.avia.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет отбор демонстрационных броней по критериям FL-01…FL-05 и командам умного фильтра. */
class BookingSpecificationsTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final LocalDate today = ctx.now.toLocalDate();
    private final BookingFilter empty = BookingFilter.empty();
    private final SmartFilterParser parser = new SmartFilterParser();

    @Test
    void emptyFilterKeepsAllBookings() {
        assertThat(select(empty)).hasSize(14);
    }

    @Test
    void filtersByStatus() {
        assertThat(select(empty.withStatus(BookingStatus.PAID)))
                .containsExactly("LM77PQ", "RT58NB", "HG23KL", "VB07MD", "QW34ER");
        assertThat(select(empty.withStatus(BookingStatus.EXPIRED))).containsExactly("WE81YT");
    }

    @Test
    void filtersBookingsInProgress() {
        assertThat(select(empty.withInProgressOnly()))
                .hasSize(10)
                .doesNotContain("AB12CD", "NR61WS", "OS44BN", "WE81YT");
    }

    @Test
    void filtersByFareClass() {
        assertThat(select(empty.withFareClass(FareClass.BUSINESS))).containsExactly("TY19GH", "HG23KL");
        assertThat(select(empty.withFareClass(FareClass.COMFORT))).hasSize(4);
    }

    @Test
    void filtersByFlightIdOrNumber() {
        assertThat(select(empty.withFlight("3"))).containsExactly("TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(select(empty.withFlight("SU1420"))).containsExactly("TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(select(empty.withFlight("su 1420"))).hasSize(3);
        assertThat(select(empty.withFlight("SU142"))).isEmpty();
        assertThat(select(empty.withFlight("99"))).isEmpty();
    }

    @Test
    void filtersByDepartureDatesInclusively() {
        assertThat(select(empty.withDepartureRange(today, today.plusDays(7)))).hasSize(7);
        assertThat(select(empty.withDepartureRange(today.plusDays(11), today.plusDays(11))))
                .containsExactly("VB07MD", "QW34ER", "WE81YT");
        assertThat(select(empty.withDepartureRange(null, today))).containsExactly("AB12CD", "NR61WS");
        assertThat(select(empty.withDepartureRange(today.plusDays(12), null))).containsExactly("PL66CX", "DF52JU");
    }

    @Test
    void filtersByPriceRange() {
        assertThat(select(empty.withPriceRange(new BigDecimal("15000"), null)))
                .containsExactly("NR61WS", "TY19GH", "HG23KL");
        assertThat(select(empty.withPriceRange(null, new BigDecimal("5200")))).hasSize(3);
        assertThat(select(empty.withPriceRange(new BigDecimal("6400.00"), new BigDecimal("6400"))))
                .containsExactly("AB12CD", "KJ90XZ");
    }

    @Test
    void appliesStrictPriceOperators() {
        assertThat(select(parser.parse("цена>15780"))).containsExactly("TY19GH", "HG23KL");
        assertThat(select(parser.parse("цена<4900"))).isEmpty();
        assertThat(select(parser.parse("цена=8300"))).containsExactly("RT58NB");
    }

    @Test
    void combinesCriteriaWithAnd() {
        assertThat(select(empty.withStatus(BookingStatus.PAID).withFareClass(FareClass.BUSINESS)))
                .containsExactly("HG23KL");
        assertThat(select(parser.parse("активные:да класс:эконом рейс:SU1420"))).containsExactly("KJ90XZ");
    }

    @Test
    void filtersByRouteAirports() {
        assertThat(select(parser.parse("откуда:svo куда:LED"))).containsExactly("AB12CD", "TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(select(parser.parse("откуда:LED"))).containsExactly("LM77PQ", "VB07MD", "QW34ER", "WE81YT");
        assertThat(select(parser.parse("куда:KZN"))).hasSize(5);
    }

    @Test
    void filtersByPassengerLastNamePart() {
        assertThat(select(parser.parse("пассажир:иванов"))).containsExactly("AB12CD", "LM77PQ", "OS44BN");
        assertThat(select(parser.parse("пассажир:ОВА"))).hasSize(5);
    }

    @Test
    void matchesFreeTextByReferenceLastNameOrFlightNumber() {
        assertThat(select(parser.parse("ab12"))).containsExactly("AB12CD");
        assertThat(select(parser.parse("СИДОР"))).containsExactly("TY19GH", "QW34ER");
        assertThat(select(parser.parse("su1420"))).containsExactly("TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(select(parser.parse("иванов SU1421"))).containsExactly("LM77PQ");
        assertThat(select(parser.parse("ZZ99ZZ"))).isEmpty();
    }

    private List<String> select(BookingFilter filter) {
        return ctx.bookings.findAll().stream()
                .filter(BookingSpecifications.from(filter))
                .map(Booking::getBookingRef)
                .toList();
    }
}
