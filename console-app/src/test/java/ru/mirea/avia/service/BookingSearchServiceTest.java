package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.filter.BookingFilter;
import ru.mirea.avia.filter.BookingSort;
import ru.mirea.avia.support.TestContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет поиск SR-01…SR-06, фильтры FL-01…FL-05, умный фильтр и сортировки SO-01…SO-05 (TC-14). */
class BookingSearchServiceTest {
    private final TestContext ctx = new TestContext().withDemoData();
    private final BookingSearchService search = ctx.searchService;
    private final LocalDate today = ctx.now.toLocalDate();
    private final BookingFilter all = BookingFilter.empty();

    @Test
    void findsBookingByReference() {
        assertThat(refs(search.byRef("ab12cd"))).containsExactly("AB12CD");
        assertThat(search.byRef("ZZ99ZZ")).isEmpty();
        assertThatThrownBy(() -> search.byRef("AB12"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
    }

    @Test
    void findsBookingsByLastNamePart() {
        assertThat(search.byLastName("иванов")).hasSize(3);
        assertThat(refs(search.byLastName(" СИДОР "))).containsExactly("TY19GH", "QW34ER");
        assertThat(search.byLastName("Смирнов")).isEmpty();
        assertThatThrownBy(() -> search.byLastName("  "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Фамилия пассажира не может быть пустой");
    }

    @Test
    void findsBookingsByDocumentNumber() {
        assertThat(refs(search.byDocumentNumber("4510 123456"))).containsExactly("AB12CD", "LM77PQ", "OS44BN");
        assertThat(search.byDocumentNumber("4510-123456")).hasSize(3);
        assertThat(refs(search.byDocumentNumber("viii-mu-123456"))).containsExactly("RT58NB", "DF52JU");
        assertThat(search.byDocumentNumber("c01x00t47")).hasSize(2);
        assertThat(search.byDocumentNumber("4510123457")).isEmpty();
        assertThatThrownBy(() -> search.byDocumentNumber(" "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Номер документа не может быть пустым");
    }

    @Test
    void findsBookingsByFlightNumber() {
        assertThat(refs(search.byFlightNumber("su1420"))).containsExactly("TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(search.byFlightNumber("SU9999")).isEmpty();
        assertThatThrownBy(() -> search.byFlightNumber("SU-1420"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
    }

    @Test
    void findsBookingsByRoute() {
        assertThat(refs(search.byRoute("svo", " LED "))).containsExactly("AB12CD", "TY19GH", "ZX45TY", "KJ90XZ");
        assertThat(search.byRoute("LED", "SVO")).hasSize(1);
        assertThat(search.byRoute("KZN", "SVO")).isEmpty();
        assertThatThrownBy(() -> search.byRoute("MOSCOW", "LED"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Код аэропорта должен состоять из трёх латинских букв, например SVO");
    }

    @Test
    void findsBookingsByDepartureDate() {
        assertThat(refs(search.byDepartureDate(today.plusDays(11)))).containsExactly("VB07MD", "QW34ER", "WE81YT");
        assertThat(search.byDepartureDate(today.plusDays(1))).hasSize(4);
        assertThat(search.byDepartureDate(today)).isEmpty();
    }

    @Test
    void filtersByStatusOrActivity() {
        assertThat(search.search(all.withStatus(BookingStatus.PAID), BookingSort.BY_ID)).hasSize(5);
        assertThat(search.search(all.withInProgressOnly(), BookingSort.BY_ID)).hasSize(10);
        assertThat(refs(search.search(all.withStatus(BookingStatus.EXPIRED), BookingSort.BY_ID)))
                .containsExactly("WE81YT");
        assertThat(refs(search.search(all.withInProgressOnly().withStatus(BookingStatus.CANCELLED), null)))
                .containsExactly("OS44BN");
    }

    @Test
    void filtersByFareClass() {
        assertThat(refs(search.search(all.withFareClass(FareClass.BUSINESS), BookingSort.BY_ID)))
                .containsExactly("TY19GH", "HG23KL");
        assertThat(search.search(all.withFareClass(FareClass.ECONOMY), BookingSort.BY_ID)).hasSize(8);
        assertThat(search.search(all.withFareClass(FareClass.COMFORT), BookingSort.BY_ID)).hasSize(4);
    }

    @Test
    void filtersByFlightIdOrNumber() {
        assertThat(search.search(all.withFlight("3"), BookingSort.BY_ID)).hasSize(3);
        assertThat(search.search(all.withFlight("SU1420"), BookingSort.BY_ID)).hasSize(3);
        assertThat(search.search(all.withFlight("su1420"), BookingSort.BY_ID)).hasSize(3);
        assertThat(search.search(all.withFlight("99"), BookingSort.BY_ID)).isEmpty();
    }

    @Test
    void filtersByDepartureDates() {
        assertThat(search.search(all.withDepartureRange(today, today.plusDays(7)), BookingSort.BY_ID)).hasSize(7);
        assertThat(search.search(all.withDepartureRange(today.plusDays(11), null), BookingSort.BY_ID)).hasSize(5);
        assertThat(search.search(all.withDepartureRange(null, today.minusDays(6)), BookingSort.BY_ID)).hasSize(2);
        assertThatThrownBy(() -> all.withDepartureRange(today, today.minusDays(1)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102")
                .hasMessage("Дата «с» не может быть позже даты «по»");
    }

    @Test
    void filtersByPriceRange() {
        assertThat(refs(search.search(all.withPriceRange(new BigDecimal("15000"), null), BookingSort.BY_ID)))
                .containsExactly("NR61WS", "TY19GH", "HG23KL");
        assertThat(search.search(all.withPriceRange(null, new BigDecimal("5200")), BookingSort.BY_ID)).hasSize(3);
        assertThat(refs(search.search(all.withPriceRange(new BigDecimal("8300"), new BigDecimal("8900")), null)))
                .containsExactly("LM77PQ", "RT58NB", "QW34ER");
        assertThatThrownBy(() -> all.withPriceRange(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Минимальная сумма не может быть больше максимальной");
    }

    @Test
    void combinesPaidAndBusinessFilters() {
        BookingFilter paidBusiness = all.withStatus(BookingStatus.PAID).withFareClass(FareClass.BUSINESS);
        assertThat(refs(search.search(paidBusiness, BookingSort.BY_ID))).containsExactly("HG23KL");
        assertThat(search.search(null, null)).hasSize(14);
    }

    @Test
    void parsesAndAppliesSmartQuery() {
        BookingFilter paidBusiness = search.parse("статус:оплачено класс:бизнес");
        assertThat(paidBusiness.status()).isEqualTo(BookingStatus.PAID);
        assertThat(paidBusiness.fareClass()).isEqualTo(FareClass.BUSINESS);
        assertThat(refs(search.search(paidBusiness, BookingSort.BY_ID))).containsExactly("HG23KL");
        assertThat(refs(search.search(search.parse("status:paid class:business"), null))).containsExactly("HG23KL");

        BookingFilter activeOnFlight = search.parse("рейс:su1420 активные:да");
        assertThat(activeOnFlight.flight()).isEqualTo("SU1420");
        assertThat(activeOnFlight.inProgressOnly()).isTrue();
        assertThat(refs(search.search(activeOnFlight, BookingSort.BY_ID)))
                .containsExactly("TY19GH", "ZX45TY", "KJ90XZ");

        BookingFilter expensiveToSochi = search.parse("откуда:svo куда:aer цена>=10000");
        assertThat(expensiveToSochi.from()).isEqualTo("SVO");
        assertThat(expensiveToSochi.to()).isEqualTo("AER");
        assertThat(expensiveToSochi.prices()).hasSize(1);
        assertThat(refs(search.search(expensiveToSochi, BookingSort.BY_PRICE_DESC)))
                .containsExactly("HG23KL", "NR61WS", "OS44BN");
    }

    @Test
    void parsesFreeTextAndDateRange() {
        BookingFilter ivanovThisMonth = search.parse("иванов с:16.09.2026 по:30.09.2026");
        assertThat(ivanovThisMonth.text()).containsExactly("иванов");
        assertThat(ivanovThisMonth.departureFrom()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(ivanovThisMonth.departureTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(refs(search.search(ivanovThisMonth, BookingSort.BY_DEPARTURE)))
                .containsExactly("LM77PQ", "OS44BN");
        assertThat(refs(search.search(search.parse("\"SU14\" рейс:1"), null))).containsExactly("AB12CD");
        assertThat(search.parse("  ").isEmpty()).isTrue();
        assertThat(search.search(search.parse(""), BookingSort.BY_ID)).hasSize(14);
    }

    @Test
    void rejectsInvalidSmartQuery() {
        assertThatThrownBy(() -> search.parse("рейс:SU-14"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103")
                .hasMessage("Номер рейса должен иметь вид SU1416 (код авиакомпании и 1–4 цифры)");
        assertThatThrownBy(() -> search.parse("откуда:MOSCOW"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
        assertThatThrownBy(() -> search.parse("статус:летит"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Неизвестный статус: летит");
        assertThatThrownBy(() -> search.parse("с:31.02.2026"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-102");
        assertThatThrownBy(() -> search.parse("цена>9000 цена<5000"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Минимальная сумма не может быть больше максимальной");
        assertThatThrownBy(() -> search.parse("x".repeat(501)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Строка фильтра слишком длинная");
    }

    @Test
    void validatesFlightCriterion() {
        assertThat(search.flightCriterion("3")).isEqualTo("3");
        assertThat(search.flightCriterion(" 42 ")).isEqualTo("42");
        assertThat(search.flightCriterion(" su1420 ")).isEqualTo("SU1420");
        assertThat(search.normalizeFlightNumber("u6 2810")).isEqualTo("U62810");
        assertThat(search.normalizeAirport(" led ")).isEqualTo("LED");
        assertThatThrownBy(() -> search.flightCriterion("SU-"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-103");
        assertThatThrownBy(() -> search.flightCriterion(""))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104")
                .hasMessage("Номер рейса не может быть пустым");
        assertThatThrownBy(() -> search.flightCriterion(null))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "E-104");
    }

    @Test
    void sortsByIdDepartureAndPrice() {
        List<BookingResponse> byId = search.search(all, BookingSort.BY_ID);
        assertThat(byId).hasSize(14).extracting(BookingResponse::id).isSorted();
        assertThat(refs(byId)).startsWith("AB12CD").endsWith("WE81YT");
        assertThat(refs(search.search(all, BookingSort.BY_DEPARTURE))).startsWith("AB12CD").endsWith("DF52JU");

        List<BookingResponse> byPrice = search.search(all, BookingSort.BY_PRICE_DESC);
        assertThat(refs(byPrice)).startsWith("HG23KL").endsWith("PL66CX");
        assertThat(byPrice.get(0).price()).isEqualTo(new BigDecimal("20750.00"));
        assertThat(byPrice).extracting(BookingResponse::price).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void sortsByLastNameCreationAndStatus() {
        List<BookingResponse> byLastName = search.search(all, BookingSort.BY_LAST_NAME);
        assertThat(byLastName.get(0).passenger().lastName()).isEqualTo("Иванов");
        assertThat(refs(byLastName)).startsWith("AB12CD", "LM77PQ", "OS44BN").endsWith("DF52JU");

        assertThat(refs(search.search(all, BookingSort.BY_CREATED_DESC)))
                .startsWith("KJ90XZ", "DF52JU", "PL66CX")
                .endsWith("AB12CD");

        List<BookingResponse> byStatus = search.search(all, BookingSort.BY_STATUS_THEN_DEPARTURE);
        assertThat(refs(byStatus)).startsWith("KJ90XZ", "PL66CX", "DF52JU").endsWith("WE81YT");
        assertThat(byStatus).extracting(BookingResponse::status).isSorted();
        assertThat(ctx.bookings.findAll()).hasSize(14);
    }

    private static List<String> refs(List<BookingResponse> bookings) {
        return bookings.stream()
                .map(BookingResponse::bookingRef)
                .toList();
    }
}
