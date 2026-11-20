package ru.mirea.avia.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.mirea.avia.domain.BookingStatus;

import java.util.List;
import java.util.UUID;

/** Теги брони и массовые операции над выбранными бронями. */
public final class BookingBulkDtos {

    private BookingBulkDtos() {
    }

    /** PUT /api/items/{id}/tags — полный новый набор тегов брони. */
    public record TagsRequest(
            @NotNull(message = "Передайте список тегов (может быть пустым)") List<String> tags,
            @NotNull(message = "Укажите version") @PositiveOrZero Long version) {
    }

    /** Тег и число броней, где он стоит (для подсказок и фильтра). */
    public record TagUsage(String tag, long count) {
    }

    public enum BulkAction { ADD_TAG, REMOVE_TAG, SET_STATUS, ARCHIVE, RESTORE }

    /** POST /api/items/bulk. Для SET_STATUS нужен status, для ADD_TAG / REMOVE_TAG — tag. */
    public record BulkRequest(
            @NotEmpty(message = "Выберите хотя бы одну бронь")
            @Size(max = 100, message = "Не больше 100 броней за раз")
            List<@NotNull UUID> ids,
            @NotNull(message = "Укажите действие") BulkAction action,
            BookingStatus status,
            String tag) {
    }

    /** Ошибка по отдельной брони: остальные брони операции это не отменяет. */
    public record BulkFailure(UUID id, String bookingCode, String message) {
    }

    public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {
    }
}
