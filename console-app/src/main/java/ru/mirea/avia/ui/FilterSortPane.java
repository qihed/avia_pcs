package ru.mirea.avia.ui;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.filter.BookingFilter;
import ru.mirea.avia.filter.BookingSort;
import ru.mirea.avia.service.BookingSearchService;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.UiText;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Раздел «Фильтрация и сортировка» (FR-13, FR-14, UC-08).
 *
 * <p>Экран хранит текущие критерии отбора и порядок сортировки. Каждый пункт заменяет только
 * свой критерий, поэтому фильтры по разным полям накапливаются, а повторный фильтр по тому же
 * полю заменяет прежний. После каждого изменения выборка перечитывается из СУБД, а при входе
 * в раздел критерии сбрасываются.</p>
 */
public final class FilterSortPane {
    private static final String IN_PROGRESS_TITLE = "Только активные (Создано, Оплачено, Регистрация)";
    private static final List<BookingSort> SORT_OPTIONS = List.of(BookingSort.BY_DEPARTURE,
            BookingSort.BY_PRICE_DESC, BookingSort.BY_LAST_NAME, BookingSort.BY_CREATED_DESC,
            BookingSort.BY_STATUS_THEN_DEPARTURE);
    private static final List<String> SMART_FILTER_HINT = List.of(
            "Условия «ключ:значение» объединяются через «И», регистр не важен:",
            "  статус:оплачено  активные:да  класс:бизнес  рейс:SU1420",
            "  откуда:SVO  куда:LED  пассажир:иванов",
            "  с:01.10.2026  по:15.10.2026  цена>=15000  цена<10000",
            "Ключи можно писать по-английски: status, active, class, flight,",
            "  from, to, passenger, since, until, price",
            "Слово без ключа ищется в номере брони, фамилии и номере рейса",
            "Пример: статус:оплачено класс:бизнес",
            "Пустая строка снимает все фильтры, сортировка сохраняется");

    private final BookingSearchService search;
    private final BookingService bookings;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    private BookingFilter filter = BookingFilter.empty();
    private BookingSort sort = BookingSort.BY_ID;
    private List<BookingResponse> current = List.of();
    private long total;

    public FilterSortPane(BookingSearchService search, BookingService bookings, ConsoleIo io,
                          InputPrompt prompt, CommandRunner runner) {
        this.search = search;
        this.bookings = bookings;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Сбрасывает критерии к полной выборке и показывает меню раздела. */
    public void show() {
        load(BookingFilter.empty(), BookingSort.BY_ID);
        Menu.of("ФИЛЬТРАЦИЯ И СОРТИРОВКА", io, prompt, runner)
                .header(this::printState)
                .item("Фильтр по статусу", "filters.status", this::byStatus)
                .item("Фильтр по классу", "filters.fareClass", this::byFareClass)
                .item("Фильтр по рейсу", "filters.flight", this::byFlight)
                .item("Фильтр по датам вылета", "filters.dates", this::byDates)
                .item("Фильтр по стоимости", "filters.price", this::byPrice)
                .item("Сортировка (5 вариантов)", "filters.sort", this::chooseSort)
                .item("Показать текущую выборку", "filters.show", () -> apply(filter, sort))
                .item("Сбросить фильтры и сортировку", "filters.reset",
                        () -> apply(BookingFilter.empty(), BookingSort.BY_ID))
                .item("Умный фильтр (строка запроса)", "filters.smart", this::smartFilter)
                .show();
    }

    private void printState() {
        Ui.info(io, "Текущая выборка: " + current.size() + " из " + total + " броней");
        Ui.info(io, "Фильтры: " + describe(filter));
        Ui.info(io, "Сортировка: " + lowerFirst(UiText.sort(sort)));
        io.println(ConsoleIo.line('-'));
    }

    /** FL-01: один статус либо все брони «в работе». */
    private void byStatus() {
        List<BookingStatus> statuses = List.of(BookingStatus.values());
        List<String> titles = new ArrayList<>(statuses.stream().map(UiText::status).toList());
        titles.add(IN_PROGRESS_TITLE);
        printOptions(titles);
        int choice = prompt.read("Статус (номер пункта)", line -> InputPrompt.parseOption(line, titles.size()));
        apply(choice == titles.size()
                ? filter.withInProgressOnly()
                : filter.withStatus(statuses.get(choice - 1)), sort);
    }

    /** FL-02. */
    private void byFareClass() {
        FareClass fareClass = prompt.readChoice("Класс обслуживания", List.of(FareClass.values()), UiText::fareClass);
        apply(filter.withFareClass(fareClass), sort);
    }

    /** FL-03: числовой ID рейса или его номер. */
    private void byFlight() {
        String flight = prompt.read("ID или номер рейса (например, 3 или SU1420)", search::flightCriterion);
        apply(filter.withFlight(flight), sort);
    }

    /** FL-04: диапазон дат вылета включительно. */
    private void byDates() {
        LocalDate from = prompt.readDate("Дата вылета «с»");
        // Фильтр сам отклоняет дату «по» раньше даты «с», поэтому такая дата запрашивается повторно.
        BookingFilter next = prompt.read("Дата вылета «по» (" + DateTimeFormat.DATE_PATTERN + ")",
                line -> filter.withDepartureRange(from, DateTimeFormat.parseDate(line)));
        apply(next, sort);
    }

    /** FL-05: диапазон стоимости, любая граница может быть не задана. */
    private void byPrice() {
        BigDecimal min = prompt.readMoney("Минимальная стоимость, руб. (Enter — без ограничения, 0 — отмена)", true);
        // Фильтр сам отклоняет максимум меньше минимума, поэтому такая сумма запрашивается повторно.
        BookingFilter next = prompt.read("Максимальная стоимость, руб. (Enter — без ограничения, 0 — отмена)",
                line -> filter.withPriceRange(min, line.isEmpty() ? null : MoneyFormat.parse(line)));
        apply(next, sort);
    }

    /** SO-01…SO-05. */
    private void chooseSort() {
        printOptions(SORT_OPTIONS.stream().map(UiText::sort).toList());
        BookingSort order = prompt.read("Порядок сортировки (номер пункта)",
                line -> SORT_OPTIONS.get(InputPrompt.parseOption(line, SORT_OPTIONS.size()) - 1));
        apply(filter, order);
    }

    private void smartFilter() {
        Ui.section(io, "УМНЫЙ ФИЛЬТР");
        SMART_FILTER_HINT.forEach(line -> Ui.info(io, line));
        io.println();
        // Строка запроса описывает выборку целиком, поэтому она заменяет все ранее заданные фильтры.
        BookingFilter next = prompt.read("Строка запроса", search::parse);
        apply(next, sort);
    }

    private void apply(BookingFilter nextFilter, BookingSort nextSort) {
        load(nextFilter, nextSort);
        printCurrent();
    }

    private void load(BookingFilter nextFilter, BookingSort nextSort) {
        // BR-09: неоплаченные в срок брони аннулируются до отбора, чтобы фильтр по статусу был точным.
        bookings.expireOutdated();
        List<BookingResponse> found = search.search(nextFilter, nextSort);
        long all = bookings.count();
        // Состояние меняется только после успешного чтения, чтобы при ошибке БД заголовок не расходился с выборкой.
        filter = nextFilter;
        sort = nextSort;
        current = found;
        total = all;
    }

    private void printCurrent() {
        io.println();
        Ui.info(io, "Выборка: " + current.size() + " из " + total + " броней");
        if (!current.isEmpty()) Ui.table(io, Cards.bookingTable(), current);
    }

    private void printOptions(List<String> titles) {
        io.println();
        for (int i = 0; i < titles.size(); i++) {
            io.println("    " + (i + 1) + ". " + titles.get(i));
        }
    }

    private static String describe(BookingFilter filter) {
        List<String> parts = new ArrayList<>();
        if (!filter.text().isEmpty()) parts.add("поиск: " + String.join(" ", filter.text()));
        if (filter.status() != null) parts.add("статус: " + UiText.statusShort(filter.status()));
        if (filter.inProgressOnly()) parts.add("статус: только активные");
        if (filter.fareClass() != null) parts.add("класс: " + UiText.fareClass(filter.fareClass()));
        if (filter.flight() != null) parts.add("рейс: " + filter.flight());
        if (filter.from() != null) parts.add("откуда: " + filter.from());
        if (filter.to() != null) parts.add("куда: " + filter.to());
        if (filter.passenger() != null) parts.add("пассажир: " + filter.passenger());
        if (filter.departureFrom() != null || filter.departureTo() != null) parts.add(describeDates(filter));
        if (!filter.prices().isEmpty()) parts.add(describePrices(filter.prices()));
        return parts.isEmpty() ? "нет" : String.join("; ", parts);
    }

    private static String describeDates(BookingFilter filter) {
        String from = DateTimeFormat.format(filter.departureFrom());
        String to = DateTimeFormat.format(filter.departureTo());
        if (filter.departureTo() == null) return "вылет с " + from;
        if (filter.departureFrom() == null) return "вылет по " + to;
        return "вылет " + from + "–" + to;
    }

    private static String describePrices(List<BookingFilter.PriceConstraint> prices) {
        return prices.stream()
                .map(price -> operator(price.operator()) + " " + MoneyFormat.format(price.value()))
                .collect(Collectors.joining(" ", "стоимость ", ""));
    }

    private static String operator(BookingFilter.Operator operator) {
        return switch (operator) {
            case LT -> "меньше";
            case LTE -> "до";
            case EQ -> "ровно";
            case GTE -> "от";
            case GT -> "больше";
        };
    }

    private static String lowerFirst(String text) {
        // Название порядка стоит в середине строки заголовка, поэтому начинается со строчной буквы.
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }
}
