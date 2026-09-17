package ru.mirea.avia.service;

import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.dto.ExportDtos.ExportFormat;
import ru.mirea.avia.dto.ExportDtos.ExportResult;
import ru.mirea.avia.export.CsvExporter;
import ru.mirea.avia.export.ExcelExporter;
import ru.mirea.avia.export.Exporter;
import ru.mirea.avia.jdbc.Transactions;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Выгрузка данных во внешние форматы (FR-16, FR-17, UC-11).
 *
 * <p>Данные читаются из СУБД в момент выгрузки, а формат файла определяет реализация
 * {@link Exporter}. Раздел экспорта работает только с интерфейсом и не знает, какой формат выбран.</p>
 */
public class ExportService {
    private final PassengerRepository passengers;
    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final StatisticsService statistics;
    private final Transactions transactions;
    private final Path directory;
    private final Clock clock;

    public ExportService(PassengerRepository passengers, FlightRepository flights,
                         BookingRepository bookings, StatisticsService statistics,
                         Transactions transactions, Path directory, Clock clock) {
        this.passengers = passengers;
        this.flights = flights;
        this.bookings = bookings;
        this.statistics = statistics;
        this.transactions = transactions;
        this.directory = directory;
        this.clock = clock;
    }

    /** Создаёт экспортёр выбранного формата; справочники и статистика для Excel читаются одной транзакцией. */
    public Exporter createExporter(ExportFormat format) {
        return switch (format) {
            case XLSX -> transactions.read(() -> new ExcelExporter(passengers.findAll(), flights.findAll(),
                    statistics.calculate(), clock));
            case CSV -> new CsvExporter(clock);
        };
    }

    /** Выгружает все бронирования указанным экспортёром в каталог из конфигурации и возвращает итог. */
    public ExportResult export(Exporter exporter) {
        List<Booking> all = transactions.read(bookings::findAll);
        Path file = exporter.export(all, directory);
        return new ExportResult(exporter.format(), file, all.size());
    }

    /** Возвращает абсолютный путь каталога выгрузки. */
    public Path directory() {
        return directory.toAbsolutePath().normalize();
    }
}
