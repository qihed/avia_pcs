package ru.mirea.avia.export;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.error.ExportException;
import ru.mirea.avia.util.DateTimeFormat;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Общая часть файловых экспортёров.
 *
 * <p>Класс формирует имя файла {@code bookings_yyyy-MM-dd_HH-mm.<расширение>}, создаёт каталог
 * выгрузки, переводит ошибки записи в код E-601 и задаёт единый набор столбцов A…M листа
 * «Бронирования» (приложение Г ТЗ). Подклассы отвечают только за запись своего формата.</p>
 */
public abstract class AbstractFileExporter implements Exporter {
    /** Заголовки столбцов A…M выгрузки бронирований. */
    protected static final List<String> BOOKING_HEADERS = List.of(
            "ID", "Номер брони (PNR)", "Пассажир", "Документ", "Рейс", "Маршрут", "Вылет",
            "Место", "Класс", "Багаж", "Стоимость, руб.", "Статус", "Создано");

    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private final ExportFormat format;
    private final Clock clock;

    protected AbstractFileExporter(ExportFormat format, Clock clock) {
        this.format = format;
        this.clock = clock;
    }

    @Override
    public ExportFormat format() {
        return format;
    }

    @Override
    public Path export(List<Booking> bookings, Path targetDir) {
        Path file = resolveTarget(targetDir);
        try {
            write(bookings, file);
            return file.toAbsolutePath().normalize();
        } catch (IOException | RuntimeException ex) {
            throw new ExportException("Не удалось создать файл " + file.toAbsolutePath().normalize()
                    + ": " + describe(ex), ex);
        }
    }

    /** Записывает брони в файл конкретного формата. */
    protected abstract void write(List<Booking> bookings, Path file) throws IOException;

    /** Возвращает маршрут рейса брони в виде «SVO → LED». */
    protected static String route(Booking booking) {
        return route(booking.getFlight());
    }

    /** Возвращает маршрут рейса в виде «SVO → LED». */
    protected static String route(Flight flight) {
        return flight.getDepartureAirport() + " → " + flight.getArrivalAirport();
    }

    /** Возвращает рейс брони в виде «SU1416 (Аэрофлот)». */
    protected static String flightTitle(Booking booking) {
        Flight flight = booking.getFlight();
        return flight.getFlightNumber() + " (" + flight.getAirline() + ")";
    }

    /** Возвращает тип и маскированный номер документа: «паспорт РФ 45 10 ****56» (NFR-09). */
    protected static String document(Passenger passenger) {
        return passenger.getDocumentType().getTitle() + " " + passenger.getMaskedDocument();
    }

    /** Возвращает значение, которое табличный редактор не примет за формулу. */
    protected static String safeCell(String value) {
        if (value == null) return "";
        if (startsLikeFormula(value)) return "'" + value;
        return value;
    }

    /** Проверяет, начинается ли значение с символа, с которого табличный редактор начинает формулу. */
    protected static boolean startsLikeFormula(String value) {
        return value != null && !value.isEmpty() && FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0;
    }

    private Path resolveTarget(Path targetDir) {
        String noAccess = "Не удалось создать файл: нет прав на запись в каталог "
                + targetDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
        } catch (IOException | SecurityException ex) {
            throw new ExportException(noAccess, ex);
        }
        if (!Files.isWritable(targetDir)) throw new ExportException(noAccess, null);
        String name = "bookings_" + DateTimeFormat.fileStamp(LocalDateTime.now(clock)) + "." + extension(format);
        return targetDir.resolve(name);
    }

    private static String describe(Exception ex) {
        if (ex instanceof AccessDeniedException) return "нет прав на запись";
        if (ex instanceof FileSystemException fileError && fileError.getReason() != null) {
            return fileError.getReason();
        }
        return "файл занят другой программой или недоступен для записи";
    }

    private static String extension(ExportFormat format) {
        return switch (format) {
            case XLSX -> "xlsx";
            case CSV -> "csv";
        };
    }
}
