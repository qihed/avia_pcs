package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.dto.TableDtos.TablePage;
import ru.mirea.avia.jdbc.Transactions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет постраничный просмотр таблиц: маскирование номеров документов и параметры страницы. */
class TableViewServiceTest {
    private static final List<String> PASSENGER_COLUMNS =
            List.of("id", "last_name", "document_type", "document_number", "email");
    private static final List<List<String>> PASSENGER_ROWS = List.of(
            List.of("1", "Иванов", "PASSPORT_RF", "4510123456", "ivanov@example.com"),
            List.of("4", "Кузнецова", "INTERNATIONAL_PASSPORT", "750123456", "kuznetsova@example.com"),
            List.of("5", "Соколов", "BIRTH_CERTIFICATE", "VIII-MU-123456", "sokolov@example.com"),
            List.of("6", "Новиков", "FOREIGN_DOCUMENT", "C01X00T47", "novikov@example.com"));
    private static final List<String> FLIGHT_COLUMNS = List.of("id", "flight_number", "document_number");
    private static final List<List<String>> FLIGHT_ROWS = List.of(List.of("3", "SU1420", "4510123456"));

    private final List<String> requests = new ArrayList<>();
    private final TableViewService service = new TableViewService(this::findPage, Transactions.direct());

    @Test
    void masksDocumentNumbersOfPassengers() {
        TablePage page = service.page(DatabaseTable.PASSENGERS, 0);
        assertThat(page.table()).isEqualTo(DatabaseTable.PASSENGERS);
        assertThat(page.columns()).isEqualTo(PASSENGER_COLUMNS);
        assertThat(page.rows().content())
                .extracting(row -> row.get(3))
                .containsExactly("45 10 ****56", "75*****56", "VIII-MU-****56", "C0*****47");
        assertThat(page.rows().content().get(0))
                .containsExactly("1", "Иванов", "PASSPORT_RF", "45 10 ****56", "ivanov@example.com");
        assertThat(page.rows().content().stream().flatMap(List::stream).toList())
                .doesNotContain("4510123456", "750123456", "VIII-MU-123456", "C01X00T47");
        assertThat(PASSENGER_ROWS.get(0).get(3)).isEqualTo("4510123456");
    }

    @Test
    void keepsPaginationOfMaskedPage() {
        TablePage page = service.page(DatabaseTable.PASSENGERS, 1);
        assertThat(page.rows().page()).isEqualTo(1);
        assertThat(page.rows().size()).isEqualTo(TableViewService.PAGE_SIZE);
        assertThat(page.rows().totalElements()).isEqualTo(45);
        assertThat(page.rows().totalPages()).isEqualTo(3);
        assertThat(page.rows().first()).isFalse();
        assertThat(page.rows().last()).isFalse();
    }

    @Test
    void leavesTablesWithoutDocumentTypeUntouched() {
        TablePage page = service.page(DatabaseTable.FLIGHTS, 0);
        assertThat(page.columns()).isEqualTo(FLIGHT_COLUMNS);
        assertThat(page.rows().content()).isEqualTo(FLIGHT_ROWS);
        assertThat(service.page(DatabaseTable.BOOKINGS, 0).rows().content()).isEmpty();
    }

    @Test
    void requestsFixedPageSizeAndClampsNegativePage() {
        service.page(DatabaseTable.BOOKINGS, -3);
        service.page(DatabaseTable.PASSENGERS, 2);
        assertThat(requests).containsExactly("BOOKINGS:0:20", "PASSENGERS:2:20");
    }

    private TablePage findPage(DatabaseTable table, int page, int size) {
        requests.add(table + ":" + page + ":" + size);
        return switch (table) {
            case PASSENGERS -> new TablePage(table, PASSENGER_COLUMNS,
                    PageResponse.from(PASSENGER_ROWS, page, size, 45));
            case FLIGHTS -> new TablePage(table, FLIGHT_COLUMNS, PageResponse.from(FLIGHT_ROWS, page, size, 1));
            case BOOKINGS -> new TablePage(table, List.of("id", "booking_ref"),
                    PageResponse.from(List.of(), page, size, 0));
        };
    }
}
