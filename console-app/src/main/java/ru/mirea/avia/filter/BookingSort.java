package ru.mirea.avia.filter;

import ru.mirea.avia.domain.Booking;

import java.util.Comparator;

/**
 * Способы сортировки бронирований (FR-14, SO-01…SO-05).
 *
 * <p>Каждый элемент хранит свой компаратор. При равенстве основного ключа порядок
 * доопределяется по ID брони, чтобы результат был стабильным.</p>
 */
public enum BookingSort {
    BY_ID(Comparator.comparing(Booking::getId, Comparator.nullsLast(Comparator.naturalOrder()))),
    BY_DEPARTURE(Comparator.comparing((Booking b) -> b.getFlight().getDepartureTime())
            .thenComparing(BY_ID.comparator)),
    BY_PRICE_DESC(Comparator.comparing(Booking::getPrice).reversed()
            .thenComparing(BY_ID.comparator)),
    BY_LAST_NAME(Comparator.comparing((Booking b) -> b.getPassenger().getLastName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(b -> b.getPassenger().getFirstName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(BY_ID.comparator)),
    BY_CREATED_DESC(Comparator.comparing(Booking::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(BY_ID.comparator)),
    BY_STATUS_THEN_DEPARTURE(Comparator.comparing(Booking::getStatus)
            .thenComparing(b -> b.getFlight().getDepartureTime())
            .thenComparing(BY_ID.comparator));

    private final Comparator<Booking> comparator;

    BookingSort(Comparator<Booking> comparator) {
        this.comparator = comparator;
    }

    public Comparator<Booking> comparator() { return comparator; }
}
