package ru.mirea.avia.ui;

import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.service.BookingSearchService;
import ru.mirea.avia.util.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Раздел «Поиск»: шесть способов найти бронирование (FR-12, SR-01…SR-06, UC-07).
 *
 * <p>Поиск по номеру брони показывает её детальную карточку, остальные способы выводят
 * таблицу найденных броней с критерием поиска и числом совпадений в заголовке.</p>
 */
public final class SearchPane {
    private final BookingSearchService search;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public SearchPane(BookingSearchService search, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        this.search = search;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню поиска, пока оператор не вернётся в главное меню. */
    public void show() {
        Menu.of("ПОИСК БРОНИРОВАНИЙ", io, prompt, runner)
                .item("По номеру брони (PNR)", "search.byRef", this::byRef)
                .item("По фамилии пассажира", "search.byLastName", this::byLastName)
                .item("По номеру документа", "search.byDocument", this::byDocument)
                .item("По номеру рейса", "search.byFlight", this::byFlightNumber)
                .item("По маршруту", "search.byRoute", this::byRoute)
                .item("По дате вылета", "search.byDate", this::byDate)
                .show();
    }

    private void byRef() {
        Ui.section(io, "ПОИСК ПО НОМЕРУ БРОНИ");
        // Формат PNR проверяется при вводе, поэтому опечатка приводит к повторному запросу, а не к пустому результату.
        List<BookingResponse> found = prompt.read("Номер брони, 6 символов (например, AB12CD)", search::byRef);
        if (found.isEmpty()) {
            Ui.empty(io, "Бронь с таким номером не найдена");
            return;
        }
        io.println();
        Cards.booking(io, found.get(0));
    }

    private void byLastName() {
        Ui.section(io, "ПОИСК ПО ФАМИЛИИ ПАССАЖИРА");
        String part = prompt.readRequired("Фамилия или её часть (регистр не важен)", "Фамилия");
        printResults("фамилия содержит «" + part + "»", search.byLastName(part));
    }

    private void byDocument() {
        Ui.section(io, "ПОИСК ПО НОМЕРУ ДОКУМЕНТА");
        String number = prompt.readRequired("Номер документа полностью (например, 4510 123456)", "Номер документа");
        printResults("документ " + number, search.byDocumentNumber(number));
    }

    private void byFlightNumber() {
        Ui.section(io, "ПОИСК ПО НОМЕРУ РЕЙСА");
        String number = prompt.read("Номер рейса (например, SU1420)", search::normalizeFlightNumber);
        printResults("рейс " + number, search.byFlightNumber(number));
    }

    private void byRoute() {
        Ui.section(io, "ПОИСК ПО МАРШРУТУ");
        String from = prompt.read("Аэропорт вылета, код IATA (например, SVO)", search::normalizeAirport);
        String to = prompt.read("Аэропорт назначения, код IATA (например, LED)", search::normalizeAirport);
        printResults("маршрут " + from + " -> " + to, search.byRoute(from, to));
    }

    private void byDate() {
        Ui.section(io, "ПОИСК ПО ДАТЕ ВЫЛЕТА");
        LocalDate date = prompt.readDate("Дата вылета");
        printResults("вылет " + DateTimeFormat.format(date), search.byDepartureDate(date));
    }

    private void printResults(String criteria, List<BookingResponse> found) {
        io.println();
        Ui.info(io, "Результаты поиска (" + criteria + "): найдено " + found.size());
        if (!found.isEmpty()) Ui.table(io, Cards.bookingTable(), found);
    }
}
