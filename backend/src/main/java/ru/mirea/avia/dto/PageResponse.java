package ru.mirea.avia.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Страница списка в стабильном JSON-формате (Spring не гарантирует формат сериализации PageImpl).
 * Поля content, number, totalElements, totalPages совпадают с тем, что уже читает web-клиент.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
