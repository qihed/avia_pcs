package ru.mirea.avia.filter;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.UrgencyLevel;
import ru.mirea.avia.error.ApiException;
import ru.mirea.avia.error.FieldViolation;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Условия поиска бронирований (GET /api/items). Все поля необязательны, пустое значение — без ограничения.
 *
 * @param q             слова через пробел; каждое должно встретиться в коде брони, ФИО, e-mail, документе
 *                      пассажира, номере рейса, аэропорте или месте
 * @param archived      false — текущие, true — архив
 * @param statuses      любой из перечисленных статусов
 * @param fareClass     класс обслуживания
 * @param flightId      конкретный рейс
 * @param passengerId   конкретный пассажир
 * @param tags          все перечисленные теги должны быть у брони
 * @param urgency       вычисляемая срочность (через окна времени до вылета, см. UrgencyLevel)
 * @param priceMin      цена не меньше
 * @param priceMax      цена не больше
 * @param departureFrom дата вылета (UTC) не раньше
 * @param departureTo   дата вылета (UTC) не позже, включительно
 * @param extraBaggage  с дополнительным багажом / без
 */
public record BookingFilter(
        String q,
        boolean archived,
        List<BookingStatus> statuses,
        FareClass fareClass,
        UUID flightId,
        UUID passengerId,
        List<String> tags,
        UrgencyLevel urgency,
        BigDecimal priceMin,
        BigDecimal priceMax,
        LocalDate departureFrom,
        LocalDate departureTo,
        Boolean extraBaggage) {

    public static final int MAX_QUERY_LENGTH = 100;
    public static final int MAX_WORDS = 5;

    public BookingFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        tags = tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).toList();
        q = q == null || q.isBlank() ? null : q.trim();
    }

    /** Только «архив/текущие» — так ведёт себя список без параметров. */
    public static BookingFilter archived(boolean archived) {
        return new BookingFilter(null, archived, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Проверка сочетаний параметров; все нарушения возвращаются сразу, по полям. */
    public void validate() {
        List<FieldViolation> errors = new ArrayList<>();
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            errors.add(new FieldViolation("q", "Не длиннее " + MAX_QUERY_LENGTH + " символов"));
        }
        if (LikePatterns.words(q).size() > MAX_WORDS) {
            errors.add(new FieldViolation("q", "Не больше " + MAX_WORDS + " слов"));
        }
        if (priceMin != null && priceMin.signum() < 0) {
            errors.add(new FieldViolation("priceMin", "Цена не может быть отрицательной"));
        }
        if (priceMax != null && priceMax.signum() < 0) {
            errors.add(new FieldViolation("priceMax", "Цена не может быть отрицательной"));
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            errors.add(new FieldViolation("priceMax", "Верхняя граница цены меньше нижней"));
        }
        if (departureFrom != null && departureTo != null && departureFrom.isAfter(departureTo)) {
            errors.add(new FieldViolation("departureTo", "Дата «по» раньше даты «с»"));
        }
        if (departureFrom != null && (departureFrom.getYear() < 1 || departureFrom.getYear() > 9999)) {
            errors.add(new FieldViolation("departureFrom", "Год должен быть от 1 до 9999"));
        }
        if (departureTo != null && (departureTo.getYear() < 1 || departureTo.getYear() > 9999)) {
            errors.add(new FieldViolation("departureTo", "Год должен быть от 1 до 9999"));
        }
        for (String tag : tags) {
            String problem = BookingTags.problem(BookingTags.normalize(tag));
            if (problem != null) {
                errors.add(new FieldViolation("tag", problem));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Проверьте условия поиска", errors);
        }
    }
}
