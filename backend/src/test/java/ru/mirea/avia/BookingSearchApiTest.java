package ru.mirea.avia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Поиск, фильтры, сортировка и пагинация списка броней GET /api/items.
 * Данные — {@link BookingFixtures}: 4 текущие брони и 1 архивная.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingSearchApiTest {

    @Autowired MockMvc mvc;
    @Autowired PassengerRepository passengers;
    @Autowired FlightRepository flights;
    @Autowired BookingRepository bookings;

    BookingFixtures data;

    @BeforeEach
    void fixture() {
        bookings.deleteAll();
        data = new BookingFixtures(passengers, flights, bookings);
    }

    /** Запрос списка с параметрами вида "ключ", "значение", "ключ", "значение"… */
    ResultActions search(String... params) throws Exception {
        var request = get("/api/items").with(BookingFixtures.as(Role.VIEWER));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    /** Коды найденных броней в любом порядке. */
    void expectCodes(ResultActions result, String... codes) throws Exception {
        result.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(codes.length));
        if (codes.length == 0) {
            result.andExpect(jsonPath("$.content", empty()));
        } else {
            result.andExpect(jsonPath("$.content[*].bookingCode", containsInAnyOrder(codes)));
        }
    }

    /** Коды найденных броней строго в этом порядке. */
    void expectOrder(ResultActions result, String... codes) throws Exception {
        result.andExpect(status().isOk()).andExpect(jsonPath("$.content[*].bookingCode", contains(codes)));
    }

    @Test
    void withoutParametersReturnsCurrentNewestFirst() throws Exception {
        expectOrder(search(), "PET004", "PET003", "IVA002", "IVA001");
        expectCodes(search("archived", "true"), "PET005");
    }

    @Nested
    class TextSearch {

        @Test
        void byPassengerLastNameIgnoringCase() throws Exception {
            expectCodes(search("q", "иванов"), "IVA001", "IVA002");
            expectCodes(search("q", "ПЕТРОВА"), "PET003", "PET004");
        }

        @Test
        void everyWordMustMatchSomeField() throws Exception {
            // «иванов» — пассажир, «su200» — рейс: остаётся одна бронь
            expectCodes(search("q", "Иванов  SU200"), "IVA002");
            expectCodes(search("q", "иванов s7300"));
        }

        @Test
        void byCodeFlightAirportSeatEmailAndDocument() throws Exception {
            expectCodes(search("q", "iva002"), "IVA002");
            expectCodes(search("q", "s73"), "PET004");
            expectCodes(search("q", "aer"), "IVA002", "PET003");
            expectCodes(search("q", "3c"), "PET003");
            expectCodes(search("q", "petrova@"), "PET003", "PET004");
            expectCodes(search("q", "654321"), "PET003", "PET004");
        }

        @Test
        void likeWildcardsAreSearchedLiterally() throws Exception {
            expectCodes(search("q", "%"));
            expectCodes(search("q", "_"));
            expectCodes(search("q", "' or 1=1 --"));
        }

        @Test
        void rejectsTooLongQuery() throws Exception {
            search("q", "x".repeat(101))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILTER"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        }
    }

    @Nested
    class Filters {

        @Test
        void bySeveralStatuses() throws Exception {
            expectCodes(search("status", "PAID", "status", "CREATED"), "IVA001", "IVA002", "PET003");
            expectCodes(search("status", "PAID,CANCELLED"), "IVA001", "PET003", "PET004");
        }

        @Test
        void byFareClassFlightAndPassenger() throws Exception {
            expectCodes(search("fareClass", "ECONOMY"), "IVA001", "PET004");
            expectCodes(search("flightId", data.su200.id.toString()), "IVA002", "PET003");
            expectCodes(search("passengerId", data.petrova.id.toString()), "PET003", "PET004");
        }

        @Test
        void byTagsCaseInsensitiveAndAllRequired() throws Exception {
            expectCodes(search("tag", "vip"), "IVA001", "IVA002");
            expectCodes(search("tag", " VIP "), "IVA001", "IVA002");
            expectCodes(search("tag", "vip", "tag", "корпоратив"), "IVA002");
            expectCodes(search("tag", "нет-такого"));
        }

        @Test
        void byPriceRangeInclusive() throws Exception {
            expectCodes(search("priceMin", "5000", "priceMax", "12000"), "IVA001", "PET003", "PET004");
            expectCodes(search("priceMin", "25000"), "IVA002");
        }

        @Test
        void byDepartureDateInclusive() throws Exception {
            String su100Day = data.su100.departureTime.toLocalDate().toString();
            String s7300Day = data.s7300.departureTime.toLocalDate().toString();
            expectCodes(search("departureFrom", s7300Day), "PET004");
            expectCodes(search("departureTo", su100Day), "IVA001");
            expectCodes(search("departureFrom", su100Day, "departureTo", s7300Day), "IVA001", "IVA002", "PET003", "PET004");
        }

        @Test
        void byComputedUrgency() throws Exception {
            expectCodes(search("urgency", "CRITICAL"), "IVA001");
            expectCodes(search("urgency", "MEDIUM"), "IVA002", "PET003");
            expectCodes(search("urgency", "LOW"));
            expectCodes(search("urgency", "NONE"), "PET004");
            expectCodes(search("urgency", "NONE", "archived", "true"), "PET005");
            // срочность в ответе совпадает с фильтром
            search("urgency", "CRITICAL").andExpect(jsonPath("$.content[0].urgencyLevel").value("CRITICAL"));
        }

        @Test
        void byExtraBaggage() throws Exception {
            expectCodes(search("extraBaggage", "true"), "IVA001");
            expectCodes(search("extraBaggage", "false"), "IVA002", "PET003", "PET004");
        }

        @Test
        void filtersCombineWithAnd() throws Exception {
            expectCodes(search("q", "иванов", "tag", "vip", "status", "CREATED"), "IVA002");
            expectCodes(search("q", "петрова", "fareClass", "ECONOMY", "urgency", "NONE"), "PET004");
        }

        @Test
        void returnsTagsInResponse() throws Exception {
            search("q", "iva002").andExpect(jsonPath("$.content[0].tags", contains("vip", "корпоратив")));
        }

        @Test
        void rejectsInconsistentOrMalformedValues() throws Exception {
            search("priceMin", "100", "priceMax", "10")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILTER"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("priceMax"));
            search("departureFrom", "2026-12-31", "departureTo", "2026-12-01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("departureTo"));
            search("priceMin", "-1").andExpect(status().isBadRequest());
            search("status", "LOST").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            search("departureFrom", "31.12.2026").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            search("flightId", "not-a-uuid").andExpect(status().isBadRequest());
            search("tag", "<script>").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        }
    }

    @Nested
    class SortingAndPaging {

        @Test
        void sortsByWhitelistedFields() throws Exception {
            expectOrder(search("sort", "price,asc"), "IVA001", "PET004", "PET003", "IVA002");
            expectOrder(search("sort", "price,desc"), "IVA002", "PET003", "PET004", "IVA001");
            expectOrder(search("sort", "departure,desc", "q", "петрова"), "PET004", "PET003");
            expectOrder(search("sort", "code"), "IVA001", "IVA002", "PET003", "PET004");
            expectOrder(search("sort", "passenger,desc", "fareClass", "ECONOMY"), "PET004", "IVA001");
        }

        @Test
        void rejectsUnknownSortField() throws Exception {
            search("sort", "passenger.email,asc")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SORT"));
            search("sort", "price,sideways").andExpect(status().isBadRequest());
        }

        @Test
        void pagesThroughResults() throws Exception {
            search("sort", "price,asc", "size", "2", "page", "1")
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.number").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.content[*].bookingCode", contains("PET003", "IVA002")));
            // за последней страницей — пустая страница, а не ошибка
            search("size", "2", "page", "5").andExpect(status().isOk()).andExpect(jsonPath("$.content", empty()));
        }

        @Test
        void rejectsBadPageParameters() throws Exception {
            search("size", "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
            search("size", "101").andExpect(status().isBadRequest());
            search("page", "-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        }
    }
}
