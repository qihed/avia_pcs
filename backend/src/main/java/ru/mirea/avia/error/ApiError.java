package ru.mirea.avia.error;

import java.time.Instant;
import java.util.List;

/** Единый JSON-формат ошибки REST API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> fieldErrors) {
}
