package ru.mirea.avia;

import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.config.StartupConnectionInfoLogger;
import ru.mirea.avia.error.ConsoleErrorHandler;
import ru.mirea.avia.error.CorrelationId;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.filter.SmartFilterParser;
import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.jdbc.MigrationRunner;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.repository.impl.BookingRepositoryImpl;
import ru.mirea.avia.repository.impl.FlightRepositoryImpl;
import ru.mirea.avia.repository.impl.PassengerRepositoryImpl;
import ru.mirea.avia.repository.impl.TableViewRepositoryImpl;
import ru.mirea.avia.service.BookingMapper;
import ru.mirea.avia.service.BookingSearchService;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.BookingValidator;
import ru.mirea.avia.service.ExportService;
import ru.mirea.avia.service.FareCalculator;
import ru.mirea.avia.service.FlightMapper;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.FlightValidator;
import ru.mirea.avia.service.PassengerMapper;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.service.PassengerValidator;
import ru.mirea.avia.service.PnrGenerator;
import ru.mirea.avia.service.StatisticsService;
import ru.mirea.avia.service.TableViewService;
import ru.mirea.avia.ui.BookingsPane;
import ru.mirea.avia.ui.CommandRunner;
import ru.mirea.avia.ui.ConsoleIo;
import ru.mirea.avia.ui.ExportPane;
import ru.mirea.avia.ui.FilterSortPane;
import ru.mirea.avia.ui.FlightsPane;
import ru.mirea.avia.ui.InputClosedException;
import ru.mirea.avia.ui.InputPrompt;
import ru.mirea.avia.ui.MainView;
import ru.mirea.avia.ui.PassengersPane;
import ru.mirea.avia.ui.SearchPane;
import ru.mirea.avia.ui.StatisticsPane;
import ru.mirea.avia.ui.TablesPane;
import ru.mirea.avia.ui.Ui;

import java.time.Clock;
import java.util.TimeZone;

/**
 * Composition root консольного приложения.
 *
 * <p>Все зависимости создаются один раз при старте и передаются экранам через
 * конструкторы; DI-контейнер не используется. Старт идёт строго по шагам: конфигурация,
 * подключение к PostgreSQL с повтором (NFR-07), миграции, стартовая памятка, аннулирование
 * просроченных броней (FR-22, BR-09) и главное меню. При выходе соединение с базой данных
 * закрывается в любом случае (FR-23).</p>
 */
public final class AviaBookingConsoleApplication {
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

    private final ConsoleIo io;
    private final InputPrompt prompt;

    public AviaBookingConsoleApplication(ConsoleIo io) {
        this.io = io;
        this.prompt = new InputPrompt(io);
    }

    /** Запускает приложение и возвращает код завершения процесса: 0 — штатный выход, 1 — сбой старта. */
    public int run() {
        AppProperties properties = loadProperties();
        if (properties == null) return EXIT_FAILURE;
        // Драйвер PostgreSQL переводит TIMESTAMP без зоны через зону JVM, поэтому она совпадает с зоной приложения.
        TimeZone.setDefault(TimeZone.getTimeZone(properties.timeZone()));
        DatabaseManager database = openDatabase(properties);
        if (database == null) return EXIT_FAILURE;

        try {
            return start(properties, database);
        } catch (InputClosedException ex) {
            io.println();
            Ui.info(io, "Ввод завершён");
            return EXIT_SUCCESS;
        } finally {
            database.close();
            Ui.info(io, "Соединение с базой данных закрыто. До свидания!");
        }
    }

    private int start(AppProperties properties, DatabaseManager database) {
        Clock clock = Clock.system(properties.timeZone());
        ConsoleErrorHandler errors = new ConsoleErrorHandler(clock);
        CommandRunner runner = new CommandRunner(errors, io);
        if (!connect(database, errors)) return EXIT_FAILURE;
        MigrationRunner migrations = new MigrationRunner(database, properties.db().demoData());
        if (!runner.run("startup.migrate", migrations::migrate)) return EXIT_FAILURE;

        Services services = createServices(properties, database, clock);
        MainView mainView = createMainView(services, runner);
        Ui.lines(io, new StartupConnectionInfoLogger(properties).banner().lines().toList());
        runner.run("startup.expire", () -> printExpired(services.bookings().expireOutdated()));
        return runner.run("main.menu", mainView::show) ? EXIT_SUCCESS : EXIT_FAILURE;
    }

    private AppProperties loadProperties() {
        try {
            return AppProperties.load();
        } catch (IllegalStateException ex) {
            printConfigError(ex.getMessage());
            return null;
        }
    }

    private DatabaseManager openDatabase(AppProperties properties) {
        try {
            return new DatabaseManager(properties.db());
        } catch (IllegalArgumentException ex) {
            // Текст исключения драйвера содержит исходный URL, в котором может быть пароль.
            printConfigError("DB_URL is not a valid PostgreSQL JDBC URL");
            return null;
        }
    }

    private void printConfigError(String message) {
        Ui.info(io, "Ошибка конфигурации: " + message);
        Ui.info(io, "Проверьте переменные в файле .env (образец — .env.example) и параметры application.properties");
    }

    private boolean connect(DatabaseManager database, ConsoleErrorHandler errors) {
        while (true) {
            Ui.info(io, "Подключение к базе данных...");
            // Проверка соединения идёт до CommandRunner, поэтому ID для журнала открывается здесь.
            String outer = CorrelationId.open();
            try {
                database.checkConnection();
                return true;
            } catch (DataAccessException ex) {
                Ui.error(io, errors.handle(ex, "startup.connect"));
            } finally {
                CorrelationId.restore(outer);
            }
            if (!askRetry()) return false;
        }
    }

    private boolean askRetry() {
        while (true) {
            io.println("    1. Повторить попытку");
            io.println("    0. Выход");
            int choice = prompt.readMenuChoice(1);
            if (choice >= 0) return choice == 1;
        }
    }

    private void printExpired(int expired) {
        if (expired > 0) Ui.info(io, "Аннулировано неоплаченных броней (BR-09): " + expired);
    }

    private MainView createMainView(Services services, CommandRunner runner) {
        PassengersPane passengersPane = new PassengersPane(services.passengers(), io, prompt, runner);
        FlightsPane flightsPane = new FlightsPane(services.flights(), io, prompt, runner);
        BookingsPane bookingsPane = new BookingsPane(services.bookings(), services.passengers(),
                services.flights(), passengersPane, io, prompt, runner);
        SearchPane searchPane = new SearchPane(services.search(), io, prompt, runner);
        FilterSortPane filterSortPane = new FilterSortPane(services.search(), services.bookings(),
                io, prompt, runner);
        StatisticsPane statisticsPane = new StatisticsPane(services.statistics(), services.bookings(), io);
        ExportPane exportPane = new ExportPane(services.exports(), services.bookings(), io, prompt, runner);
        TablesPane tablesPane = new TablesPane(services.tables(), io, prompt, runner);
        return new MainView(io, prompt, runner,
                services.passengers(), services.flights(), services.bookings(),
                passengersPane, flightsPane, bookingsPane,
                searchPane, filterSortPane, statisticsPane,
                exportPane, tablesPane);
    }

    private static Services createServices(AppProperties properties, DatabaseManager database, Clock clock) {
        PassengerRepository passengers = new PassengerRepositoryImpl(database);
        FlightRepository flights = new FlightRepositoryImpl(database);
        BookingRepository bookings = new BookingRepositoryImpl(database);
        PassengerMapper passengerMapper = new PassengerMapper(clock);
        FlightMapper flightMapper = new FlightMapper();
        BookingMapper bookingMapper = new BookingMapper(passengerMapper, flightMapper);
        FlightValidator flightValidator = new FlightValidator();
        BookingValidator bookingValidator = new BookingValidator(bookings, clock, properties.booking());

        PassengerService passengerService = new PassengerService(passengers, bookings,
                new PassengerValidator(clock), passengerMapper, database, clock);
        FlightService flightService = new FlightService(flights, bookings, flightValidator, flightMapper,
                database, clock, properties.booking().saleCloseBefore());
        BookingService bookingService = new BookingService(bookings, passengers, flights, bookingValidator,
                new FareCalculator(), new PnrGenerator(), bookingMapper, database, clock,
                properties.booking().expireAfter());
        BookingSearchService searchService = new BookingSearchService(bookings, flights, bookingValidator,
                flightValidator, new SmartFilterParser(), bookingMapper, database);
        StatisticsService statisticsService = new StatisticsService(passengers, flights, bookings,
                flightMapper, database);
        ExportService exportService = new ExportService(passengers, flights, bookings, statisticsService,
                database, properties.export().directory(), clock);
        TableViewService tableService = new TableViewService(new TableViewRepositoryImpl(database), database);
        return new Services(passengerService, flightService, bookingService, searchService,
                statisticsService, exportService, tableService);
    }

    /** Прикладные сервисы, которые получают экраны главного меню. */
    private record Services(PassengerService passengers, FlightService flights, BookingService bookings,
                            BookingSearchService search, StatisticsService statistics,
                            ExportService exports, TableViewService tables) {
    }
}
