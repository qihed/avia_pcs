package ru.mirea.avia.filter;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.mirea.avia.error.BadRequestException;

import java.util.Locale;
import java.util.Map;

/**
 * Pageable из параметров запроса page, size, sort. Сортировать можно только по полям из белого списка:
 * клиент передаёт короткое имя ("price,desc"), а в запрос попадает путь атрибута сущности ("price").
 * Произвольную строку из запроса в сортировку не подставляем.
 */
public final class PageRequests {

    private PageRequests() {
    }

    /**
     * @param sort      строка вида "поле" или "поле,asc|desc"; пустая — сортировка по умолчанию
     * @param allowed   короткое имя поля → путь атрибута JPA
     * @param fallback  сортировка по умолчанию, например "createdAt,desc"
     * @param tieBreak  атрибут для стабильного порядка при равных значениях (обычно "id")
     */
    public static Pageable of(int page, int size, String sort, int maxSize,
                              Map<String, String> allowed, String fallback, String tieBreak) {
        if (page < 0) {
            throw new BadRequestException("INVALID_PAGE", "Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > maxSize) {
            throw new BadRequestException("INVALID_PAGE_SIZE", "Размер страницы должен быть от 1 до " + maxSize);
        }
        String source = sort == null || sort.isBlank() ? fallback : sort.trim();
        String[] parts = source.split(",", -1);
        if (parts.length > 2) {
            throw new BadRequestException("INVALID_SORT", "Сортировка задаётся как «поле» или «поле,asc|desc»");
        }
        String property = allowed.get(parts[0].trim());
        if (property == null) {
            throw new BadRequestException("INVALID_SORT",
                    "Сортировка по полю «" + parts[0].trim() + "» недоступна. Доступно: " + String.join(", ", allowed.keySet()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                case "asc", "" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new BadRequestException("INVALID_SORT", "Направление сортировки: asc или desc");
            };
        }
        Sort order = Sort.by(direction, property);
        if (tieBreak != null && !tieBreak.equals(property)) {
            order = order.and(Sort.by(Sort.Direction.ASC, tieBreak));
        }
        return PageRequest.of(page, size, order);
    }
}
