package ru.mirea.avia.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.UrgencyLevel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Превращает {@link BookingFilter} в JPA Specification (Criteria API). Строка из запроса никогда не
 * склеивается с SQL: значения уходят в запрос параметрами, LIKE-шаблоны экранируются.
 */
public final class BookingSpecifications {

    /** Поля сортировки: имя в запросе → путь атрибута. Остальные поля сортировать нельзя. */
    public static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "price", "price",
            "departure", "flight.departureTime",
            "passenger", "passenger.lastName",
            "flight", "flight.flightNumber",
            "code", "bookingCode",
            "seat", "seatNumber",
            "status", "status");

    public static final String DEFAULT_SORT = "createdAt,desc";

    /** Статусы, у которых срочность всегда NONE (как в UrgencyLevel.calculate). */
    private static final List<BookingStatus> TERMINAL = List.of(
            BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED);

    private BookingSpecifications() {
    }

    /** @param now текущее время UTC — от него считается срочность */
    public static Specification<Booking> from(BookingFilter filter, LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("archived"), filter.archived()));

            for (String word : LikePatterns.words(filter.q())) {
                p.add(anyFieldContains(root, cb, LikePatterns.contains(word)));
            }
            if (!filter.statuses().isEmpty()) {
                p.add(root.get("status").in(filter.statuses()));
            }
            if (filter.fareClass() != null) {
                p.add(cb.equal(root.get("fareClass"), filter.fareClass()));
            }
            if (filter.flightId() != null) {
                p.add(cb.equal(root.get("flight").get("id"), filter.flightId()));
            }
            if (filter.passengerId() != null) {
                p.add(cb.equal(root.get("passenger").get("id"), filter.passengerId()));
            }
            for (String tag : filter.tags()) {
                p.add(cb.isMember(BookingTags.normalize(tag), root.get("tags")));
            }
            if (filter.priceMin() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("price"), filter.priceMin()));
            }
            if (filter.priceMax() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("price"), filter.priceMax()));
            }
            Path<LocalDateTime> departure = root.get("flight").get("departureTime");
            if (filter.departureFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(departure, filter.departureFrom().atStartOfDay()));
            }
            if (filter.departureTo() != null) {
                p.add(cb.lessThan(departure, filter.departureTo().plusDays(1).atStartOfDay()));
            }
            if (filter.extraBaggage() != null) {
                p.add(cb.equal(root.get("extraBaggage"), filter.extraBaggage()));
            }
            if (filter.urgency() != null) {
                p.add(urgency(root, cb, filter.urgency(), now));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Слово ищется во всех текстовых полях брони, пассажира и рейса. */
    private static Predicate anyFieldContains(Root<Booking> root, CriteriaBuilder cb, String like) {
        Path<Object> passenger = root.get("passenger");
        Path<Object> flight = root.get("flight");
        List<Path<String>> fields = List.of(
                root.get("bookingCode"),
                root.get("seatNumber"),
                passenger.get("lastName"),
                passenger.get("firstName"),
                passenger.get("middleName"),
                passenger.get("email"),
                passenger.get("documentNumber"),
                flight.get("flightNumber"),
                flight.get("departureAirport"),
                flight.get("arrivalAirport"));
        return cb.or(fields.stream()
                .map(f -> cb.like(cb.lower(f), like, LikePatterns.ESCAPE))
                .toArray(Predicate[]::new));
    }

    /**
     * Срочность не хранится в БД, а вычисляется (UrgencyLevel.calculate). Для поиска переводим её в
     * условия на время вылета с теми же включёнными границами: CRITICAL — вылет в (now, now+6ч],
     * HIGH — (now+6ч, now+24ч], MEDIUM — (now+24ч, now+72ч], LOW — позже now+72ч.
     * NONE — завершённые/отменённые/просроченные, архивные или с уже наступившим вылетом.
     */
    private static Predicate urgency(Root<Booking> root, CriteriaBuilder cb, UrgencyLevel level, LocalDateTime now) {
        Path<LocalDateTime> departure = root.get("flight").get("departureTime");
        Predicate inactive = cb.or(
                cb.isTrue(root.<Boolean>get("archived")),
                root.get("status").in(TERMINAL),
                cb.lessThanOrEqualTo(departure, now));
        if (level == UrgencyLevel.NONE) {
            return inactive;
        }
        LocalDateTime lower = switch (level) {
            case CRITICAL -> now;
            case HIGH -> now.plusHours(6);
            case MEDIUM -> now.plusHours(24);
            case LOW -> now.plusHours(72);
            default -> throw new IllegalStateException();
        };
        List<Predicate> window = new ArrayList<>();
        window.add(cb.not(inactive));
        window.add(cb.greaterThan(departure, lower));
        LocalDateTime upper = switch (level) {
            case CRITICAL -> now.plusHours(6);
            case HIGH -> now.plusHours(24);
            case MEDIUM -> now.plusHours(72);
            default -> null;
        };
        if (upper != null) {
            window.add(cb.lessThanOrEqualTo(departure, upper));
        }
        return cb.and(window.toArray(Predicate[]::new));
    }
}
