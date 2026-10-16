package ru.mirea.avia.fx.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.FlightStatus;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Экран рейсов: список рейсов и бронирования выбранного рейса. */
public final class FlightsController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final FlightService flights;
    private final BookingService bookings;

    @FXML private TableView<Flight> flightsTable;
    @FXML private TableColumn<Flight, String> numberColumn;
    @FXML private TableColumn<Flight, String> airlineColumn;
    @FXML private TableColumn<Flight, String> routeColumn;
    @FXML private TableColumn<Flight, LocalDateTime> departureColumn;
    @FXML private TableColumn<Flight, LocalDateTime> arrivalColumn;
    @FXML private TableColumn<Flight, Integer> seatsColumn;
    @FXML private TableColumn<Flight, BigDecimal> priceColumn;
    @FXML private TableColumn<Flight, FlightStatus> flightStatusColumn;
    @FXML private Label flightsCountLabel;

    @FXML private Label selectedFlightLabel;
    @FXML private TableView<Booking> bookingsTable;
    @FXML private TableColumn<Booking, Long> bookingIdColumn;
    @FXML private TableColumn<Booking, String> passengerColumn;
    @FXML private TableColumn<Booking, String> seatColumn;
    @FXML private TableColumn<Booking, String> fareColumn;
    @FXML private TableColumn<Booking, BigDecimal> bookingPriceColumn;
    @FXML private TableColumn<Booking, BookingStatus> bookingStatusColumn;
    @FXML private Label bookingsCountLabel;

    public FlightsController(FlightService flights, BookingService bookings) {
        this.flights = flights;
        this.bookings = bookings;
    }

    @FXML
    private void initialize() {
        numberColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getFlightNumber()));
        airlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getAirline()));
        routeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getRoute()));
        departureColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getDepartureTime()));
        arrivalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getArrivalTime()));
        seatsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getTotalSeats()));
        priceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getBasePrice()));
        flightStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getStatus()));

        formatDateColumn(departureColumn);
        formatDateColumn(arrivalColumn);
        flightStatusColumn.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(FlightStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.getTitle());
            }
        });

        bookingIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getId()));
        passengerColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getPassenger().getFullName()));
        seatColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getSeatNumber()));
        fareColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getFareClass().getTitle()));
        bookingPriceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getPrice()));
        bookingStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().getStatus()));
        bookingStatusColumn.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(BookingStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.getShortTitle());
            }
        });

        flightsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> loadBookings(selected));
        refresh();
    }

    @FXML
    public void refresh() {
        Flight selected = flightsTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1 : selected.getId();
        List<Flight> all = flights.list();
        flightsTable.setItems(FXCollections.observableArrayList(all));
        flightsCountLabel.setText("Рейсов: " + all.size());
        all.stream().filter(f -> f.getId() == selectedId).findFirst()
                .ifPresentOrElse(flightsTable.getSelectionModel()::select,
                        () -> flightsTable.getSelectionModel().clearSelection());
        loadBookings(flightsTable.getSelectionModel().getSelectedItem());
    }

    private void loadBookings(Flight flight) {
        if (flight == null) {
            selectedFlightLabel.setText("Выберите рейс в верхней таблице");
            bookingsTable.getItems().clear();
            bookingsCountLabel.setText("Бронирований: 0");
            return;
        }
        try {
            List<Booking> selected = bookings.list().stream()
                    .filter(b -> b.getFlight().getId() == flight.getId())
                    .toList();
            selectedFlightLabel.setText("Бронирования рейса " + flight.getFlightNumber() + " · " + flight.getRoute());
            bookingsTable.setItems(FXCollections.observableArrayList(selected));
            bookingsCountLabel.setText("Бронирований: " + selected.size());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Не удалось загрузить бронирования рейса.\n" + e.getMessage()).showAndWait();
        }
    }

    private void formatDateColumn(TableColumn<Flight, LocalDateTime> column) {
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : DATE_TIME.format(value));
            }
        });
    }
}
