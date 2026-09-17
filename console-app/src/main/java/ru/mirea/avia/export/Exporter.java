package ru.mirea.avia.export;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.error.ExportException;

import java.nio.file.Path;
import java.util.List;

/**
 * Общий контракт выгрузки бронирований в файл (FR-16, FR-17).
 *
 * <p>Раздел экспорта работает со ссылкой этого типа и не знает, какой формат выбран. Экспортёр
 * принимает доменные брони напрямую: это внутренний слой выгрузки, а не публичный API сервиса.</p>
 */
public interface Exporter {
    /** Возвращает формат файла, который создаёт экспортёр. */
    ExportFormat format();

    /**
     * Выгружает брони в файл {@code bookings_yyyy-MM-dd_HH-mm.<расширение>} в каталоге {@code targetDir}
     * и возвращает полный путь к нему; ошибка записи сообщается {@link ExportException} (E-601).
     */
    Path export(List<Booking> bookings, Path targetDir) throws ExportException;
}
