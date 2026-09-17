package ru.mirea.avia.dto;

import java.util.List;

/** Страница результата для постраничного вывода (NFR-04). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Собирает страницу из уже выбранных строк и общего числа записей. */
    public static <T> PageResponse<T> from(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(List.copyOf(content), page, size, totalElements, totalPages,
                page == 0, page >= totalPages - 1);
    }

    /** Вырезает страницу из списка, уже загруженного в память. */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return from(all.subList(from, to), page, size, all.size());
    }
}
