package ru.mirea.avia.ui;

import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;

/**
 * Главное меню приложения (п. 6.2 ТЗ) со счётчиками записей в заголовке.
 *
 * <p>Экран только связывает разделы: каждый пункт открывает готовую панель, а счётчики
 * пересчитываются при каждом показе меню. Если база данных недоступна, меню всё равно
 * выводится, чтобы оператор мог повторить команду позже или выйти (NFR-07).</p>
 */
public final class MainView {
    private static final String TITLE = "ИС «БРОНИРОВАНИЕ АВИАБИЛЕТОВ» · АВИА-БРОНЬ v1.0";

    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;
    private final PassengerService passengers;
    private final FlightService flights;
    private final BookingService bookings;
    private final PassengersPane passengersPane;
    private final FlightsPane flightsPane;
    private final BookingsPane bookingsPane;
    private final SearchPane searchPane;
    private final FilterSortPane filterSortPane;
    private final StatisticsPane statisticsPane;
    private final ExportPane exportPane;
    private final TablesPane tablesPane;

    public MainView(ConsoleIo io, InputPrompt prompt, CommandRunner runner,
                    PassengerService passengers, FlightService flights, BookingService bookings,
                    PassengersPane passengersPane, FlightsPane flightsPane, BookingsPane bookingsPane,
                    SearchPane searchPane, FilterSortPane filterSortPane, StatisticsPane statisticsPane,
                    ExportPane exportPane, TablesPane tablesPane) {
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
        this.passengers = passengers;
        this.flights = flights;
        this.bookings = bookings;
        this.passengersPane = passengersPane;
        this.flightsPane = flightsPane;
        this.bookingsPane = bookingsPane;
        this.searchPane = searchPane;
        this.filterSortPane = filterSortPane;
        this.statisticsPane = statisticsPane;
        this.exportPane = exportPane;
        this.tablesPane = tablesPane;
    }

    /** Показывает главное меню, пока оператор не выберет «Выход». */
    public void show() {
        Menu.of(TITLE, io, prompt, runner)
                .header(this::printCounters)
                .section("Пассажиры", "main.passengers", passengersPane::show)
                .section("Рейсы", "main.flights", flightsPane::show)
                .section("Бронирования", "main.bookings", bookingsPane::show)
                .section("Поиск", "main.search", searchPane::show)
                .section("Фильтрация и сортировка", "main.filters", filterSortPane::show)
                .item("Статистика", "main.statistics", statisticsPane::show)
                .section("Экспорт данных", "main.export", exportPane::show)
                .section("Вывести таблицы базы данных", "main.tables", tablesPane::show)
                .exitTitle("Выход")
                .show();
    }

    private void printCounters() {
        try {
            // Счётчики читаются по очереди: при недоступной БД ожидание таймаута происходит один раз.
            Ui.info(io, "Пассажиров: " + passengers.count()
                    + " · Рейсов: " + flights.count()
                    + " · Активных броней: " + bookings.countActive());
        } catch (DataAccessException ex) {
            Ui.info(io, "Нет связи с базой данных — счётчики недоступны");
        }
        io.println();
    }
}
