package ru.mirea.avia.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.service.BookingService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Экран статистики: 6 показателей системы и распределение бронирований по статусам. */
public final class StatisticsController {
    private final BookingService bookings;

    @FXML private Label passengersValue;
    @FXML private Label flightsValue;
    @FXML private Label bookingsValue;
    @FXML private Label activeValue;
    @FXML private Label completedValue;
    @FXML private Label cancelledValue;
    @FXML private BarChart<String, Number> statusChart;

    public StatisticsController(BookingService bookings) {
        this.bookings = bookings;
    }

    @FXML
    private void initialize() {
        refresh();
    }

    @FXML
    public void refresh() {
        try {
            Map<String, Long> statistics = bookings.statistics();
            passengersValue.setText(value(statistics, "Всего пассажиров"));
            flightsValue.setText(value(statistics, "Всего рейсов"));
            bookingsValue.setText(value(statistics, "Всего бронирований"));
            activeValue.setText(value(statistics, "Активных броней"));
            completedValue.setText(value(statistics, "Завершённых броней"));
            cancelledValue.setText(value(statistics, "Отменённых броней"));
            updateChart(bookings.list());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Не удалось загрузить статистику.\n" + e.getMessage()).showAndWait();
        }
    }

    private String value(Map<String, Long> statistics, String key) {
        return String.valueOf(statistics.getOrDefault(key, 0L));
    }

    private void updateChart(List<Booking> all) {
        Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) counts.put(status, 0L);
        for (Booking booking : all) counts.merge(booking.getStatus(), 1L, Long::sum);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Бронирования");
        for (BookingStatus status : BookingStatus.values()) {
            series.getData().add(new XYChart.Data<>(status.getShortTitle(), counts.get(status)));
        }
        statusChart.setData(FXCollections.observableArrayList(series));
    }
}
