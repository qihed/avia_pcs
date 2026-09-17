package ru.mirea.avia.filter;

import ru.mirea.avia.domain.Booking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Собирает условие Stream API из разобранного фильтра бронирований.
 *
 * <p>Заданные критерии объединяются через AND, незаданный критерий ничего не ограничивает.
 * Отбор выполняется над уже загруженной выборкой (FR-13), поэтому значения фильтра
 * никогда не попадают в текст SQL.</p>
 */
public final class BookingSpecifications {
    private static final Pattern FLIGHT_ID = Pattern.compile("^\\d{1,18}$");

    private BookingSpecifications() { }

    /** Возвращает условие, которому удовлетворяют брони, прошедшие все заданные критерии. */
    public static Predicate<Booking> from(BookingFilter filter) {
        List<Predicate<Booking>> predicates = new ArrayList<>();
        if (filter.status() != null) predicates.add(b -> b.getStatus() == filter.status());
        if (filter.inProgressOnly()) predicates.add(b -> b.getStatus().isInProgress());
        if (filter.fareClass() != null) predicates.add(b -> b.getFareClass() == filter.fareClass());
        if (filter.flight() != null) predicates.add(flight(filter.flight()));
        if (filter.from() != null) {
            predicates.add(b -> equalsIgnoreCase(b.getFlight().getDepartureAirport(), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(b -> equalsIgnoreCase(b.getFlight().getArrivalAirport(), filter.to()));
        }
        if (filter.passenger() != null) {
            predicates.add(b -> contains(b.getPassenger().getLastName(), filter.passenger()));
        }
        if (filter.departureFrom() != null) predicates.add(b -> !departureDate(b).isBefore(filter.departureFrom()));
        if (filter.departureTo() != null) predicates.add(b -> !departureDate(b).isAfter(filter.departureTo()));
        filter.text().forEach(word -> predicates.add(freeText(word)));
        filter.prices().forEach(constraint -> predicates.add(price(constraint)));
        return predicates.stream().reduce(b -> true, Predicate::and);
    }

    private static Predicate<Booking> flight(String value) {
        String criterion = value.replaceAll("\\s+", "");
        // Номер рейса всегда начинается с кода авиакомпании, поэтому строка из одних цифр — это ID рейса.
        if (FLIGHT_ID.matcher(criterion).matches()) {
            Long id = Long.valueOf(criterion);
            return b -> Objects.equals(b.getFlight().getId(), id);
        }
        return b -> equalsIgnoreCase(b.getFlight().getFlightNumber(), criterion);
    }

    private static Predicate<Booking> freeText(String word) {
        return b -> contains(b.getBookingRef(), word)
                || contains(b.getPassenger().getLastName(), word)
                || contains(b.getFlight().getFlightNumber(), word);
    }

    private static Predicate<Booking> price(BookingFilter.PriceConstraint constraint) {
        return b -> matches(constraint.operator(), b.getPrice().compareTo(constraint.value()));
    }

    private static boolean matches(BookingFilter.Operator operator, int comparison) {
        return switch (operator) {
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            case EQ -> comparison == 0;
            case GTE -> comparison >= 0;
            case GT -> comparison > 0;
        };
    }

    private static LocalDate departureDate(Booking booking) {
        return booking.getFlight().getDepartureTime().toLocalDate();
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected.trim());
    }

    private static boolean contains(String value, String part) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(part.trim().toLowerCase(Locale.ROOT));
    }
}
