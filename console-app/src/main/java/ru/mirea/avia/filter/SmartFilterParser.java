package ru.mirea.avia.filter;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает строку умного фильтра бронирований в типобезопасный {@link BookingFilter}.
 *
 * <p>Парсер принимает только известные команды вида {@code ключ:значение} и ограничения
 * стоимости вида {@code цена>=5000}; остальные слова считаются свободным поиском. Строка
 * фильтра никогда не попадает в SQL: отбор выполняет {@link BookingSpecifications}.</p>
 */
public final class SmartFilterParser {
    private static final int MAX_LENGTH = 500;
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Pattern PRICE = Pattern.compile("^(цена|price)(<=|>=|<|>|=)(\\d+([.,]\\d{1,2})?)$");

    /** Разбирает строку; пустая строка означает фильтр без ограничений. */
    public BookingFilter parse(String source) {
        if (source == null || source.isBlank()) return BookingFilter.empty();
        if (source.length() > MAX_LENGTH) {
            throw new ValidationException(ErrorCode.E_103, "Строка фильтра слишком длинная");
        }

        Criteria criteria = new Criteria();
        Matcher matcher = TOKEN.matcher(source.trim());
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String token = raw.trim();
            if (token.isEmpty()) continue;
            String lower = token.toLowerCase(Locale.ROOT);

            Matcher priceMatcher = PRICE.matcher(lower);
            if (priceMatcher.matches()) {
                criteria.prices.add(new BookingFilter.PriceConstraint(
                        operator(priceMatcher.group(2)), MoneyFormat.parse(priceMatcher.group(3))));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon > 0) {
                String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
                criteria.apply(key, stripQuotes(token.substring(colon + 1).trim()), token);
            } else {
                criteria.free.add(token);
            }
        }
        return criteria.toFilter();
    }

    private static BookingFilter.Operator operator(String value) {
        return switch (value) {
            case "<" -> BookingFilter.Operator.LT;
            case "<=" -> BookingFilter.Operator.LTE;
            case "=" -> BookingFilter.Operator.EQ;
            case ">=" -> BookingFilter.Operator.GTE;
            case ">" -> BookingFilter.Operator.GT;
            default -> throw new ValidationException(ErrorCode.E_103, "Неизвестный оператор стоимости: " + value);
        };
    }

    private static BookingStatus status(String value) {
        return switch (value.replace('-', '_')) {
            case "создано", "created" -> BookingStatus.CREATED;
            case "оплачено", "paid" -> BookingStatus.PAID;
            case "регистрация", "checked_in" -> BookingStatus.CHECKED_IN;
            case "выполнено", "completed" -> BookingStatus.COMPLETED;
            case "отменено", "cancelled", "canceled" -> BookingStatus.CANCELLED;
            case "просрочено", "expired" -> BookingStatus.EXPIRED;
            default -> throw new ValidationException(ErrorCode.E_103, "Неизвестный статус: " + value);
        };
    }

    private static FareClass fareClass(String value) {
        return switch (value) {
            case "эконом", "economy" -> FareClass.ECONOMY;
            case "комфорт", "comfort" -> FareClass.COMFORT;
            case "бизнес", "business" -> FareClass.BUSINESS;
            default -> throw new ValidationException(ErrorCode.E_103, "Неизвестный класс обслуживания: " + value);
        };
    }

    private static boolean booleanValue(String value, String field) {
        return switch (value) {
            case "да", "yes", "true" -> true;
            case "нет", "no", "false" -> false;
            default -> throw new ValidationException(ErrorCode.E_103, "Поле «" + field + "» принимает да/нет");
        };
    }

    private static String stripQuotes(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Накапливает значения команд, пока строка фильтра разбирается по словам. */
    private static final class Criteria {
        private final List<String> free = new ArrayList<>();
        private final List<BookingFilter.PriceConstraint> prices = new ArrayList<>();
        private BookingStatus status;
        private boolean inProgressOnly;
        private FareClass fareClass;
        private String flight;
        private String from;
        private String to;
        private String passenger;
        private LocalDate departureFrom;
        private LocalDate departureTo;

        private void apply(String key, String value, String token) {
            String normalized = value.toLowerCase(Locale.ROOT);
            switch (key) {
                case "статус", "status" -> status = status(normalized);
                case "активные", "active" -> inProgressOnly = booleanValue(normalized, "активные");
                case "класс", "class" -> fareClass = fareClass(normalized);
                case "рейс", "flight" -> flight = value;
                case "откуда", "from" -> from = value;
                case "куда", "to" -> to = value;
                case "пассажир", "passenger" -> passenger = value;
                case "с", "since" -> departureFrom = DateTimeFormat.parseDate(value);
                case "по", "until" -> departureTo = DateTimeFormat.parseDate(value);
                default -> free.add(token);
            }
        }

        private BookingFilter toFilter() {
            return new BookingFilter(List.copyOf(free), status, inProgressOnly, fareClass,
                    blankToNull(flight), blankToNull(from), blankToNull(to), blankToNull(passenger),
                    departureFrom, departureTo, List.copyOf(prices));
        }
    }
}
