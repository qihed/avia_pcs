package ru.mirea.avia.fx.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.DocumentType;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.PassengerService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Экран «Пассажиры»: таблица пассажиров с поиском и фильтром по документу, сортировка кликом по заголовку,
 * под ней — брони выбранного пассажира. Добавление и изменение — через PassengerFormDialog, удаление —
 * с подтверждением и проверкой, что на пассажира нет броней. SQL здесь нет — только вызовы сервисов.
 */
public final class PassengerViewController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML private TextField searchField;
    @FXML private ComboBox<DocumentType> documentTypeCombo;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    @FXML private TableView<Passenger> table;
    @FXML private TableColumn<Passenger, Long> idColumn;
    @FXML private TableColumn<Passenger, String> nameColumn;
    @FXML private TableColumn<Passenger, LocalDate> birthDateColumn;
    @FXML private TableColumn<Passenger, String> documentColumn;
    @FXML private TableColumn<Passenger, String> emailColumn;
    @FXML private TableColumn<Passenger, String> phoneColumn;
    @FXML private TableColumn<Passenger, Integer> bookingsColumn;
    @FXML private Label countLabel;

    @FXML private Label bookingsTitle;
    @FXML private Label bookingsSummary;
    @FXML private Label bookingsPlaceholder;
    @FXML private TableView<Booking> bookingsTable;
    @FXML private TableColumn<Booking, Long> bookingIdColumn;
    @FXML private TableColumn<Booking, String> bookingFlightColumn;
    @FXML private TableColumn<Booking, String> bookingRouteColumn;
    @FXML private TableColumn<Booking, LocalDateTime> bookingDepartureColumn;
    @FXML private TableColumn<Booking, String> bookingSeatColumn;
    @FXML private TableColumn<Booking, FareClass> bookingFareClassColumn;
    @FXML private TableColumn<Booking, BigDecimal> bookingPriceColumn;
    @FXML private TableColumn<Booking, BookingStatus> bookingStatusColumn;

    private final PassengerService passengers;
    private final BookingService bookings;
    private final ObservableList<Passenger> items = FXCollections.observableArrayList();
    private final FilteredList<Passenger> filtered = new FilteredList<>(items);
    private final SortedList<Passenger> sorted = new SortedList<>(filtered);
    private final ObservableList<Booking> passengerBookings = FXCollections.observableArrayList();
    private final SortedList<Booking> sortedBookings = new SortedList<>(passengerBookings);
    /** ID пассажира → его брони; перечитывается вместе с таблицей. */
    private Map<Long, List<Booking>> bookingsByPassenger = Map.of();

    public PassengerViewController(PassengerService passengers, BookingService bookings) {
        this.passengers = passengers;
        this.bookings = bookings;
    }

    @FXML
    private void initialize() {
        documentTypeCombo.getItems().setAll(DocumentType.values());
        documentTypeCombo.setConverter(FormFields.titles(DocumentType::getTitle));
        searchField.textProperty().addListener((o, a, b) -> applyFilter());
        documentTypeCombo.valueProperty().addListener((o, a, b) -> applyFilter());

        setupColumns();
        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());   // сортировка кликом по заголовку
        sorted.addListener((ListChangeListener<Passenger>) c -> updateCount());
        bookingsTable.setItems(sortedBookings);
        sortedBookings.comparatorProperty().bind(bookingsTable.comparatorProperty());

        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, passenger) -> showBookings(passenger));
        // Двойной клик по строке — то же, что кнопка «Изменить».
        table.setRowFactory(t -> {
            TableRow<Passenger> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) onEdit();
            });
            return row;
        });

        showBookings(null);
        onRefresh();
    }

    private void setupColumns() {
        idColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getId()));
        nameColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFullName()));
        birthDateColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getBirthDate()));
        documentColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getDocumentType().getTitle() + " " + d.getValue().getDocumentNumber()));
        emailColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getEmail()));
        phoneColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPhone()));
        bookingsColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(bookingsOf(d.getValue()).size()));
        birthDateColumn.setCellFactory(c -> TableCells.text(FormFields.DATE::format));

        bookingIdColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getId()));
        bookingFlightColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getFlight().getFlightNumber()));
        bookingRouteColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFlight().getRoute()));
        bookingDepartureColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getFlight().getDepartureTime()));
        bookingSeatColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getSeatNumber()));
        bookingFareClassColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFareClass()));
        bookingPriceColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPrice()));
        bookingStatusColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getStatus()));
        bookingDepartureColumn.setCellFactory(c -> TableCells.text(DATE_TIME::format));
        bookingFareClassColumn.setCellFactory(c -> TableCells.text(FareClass::getTitle));
        bookingPriceColumn.setCellFactory(c -> TableCells.text(BigDecimal::toPlainString));
        bookingStatusColumn.setCellFactory(c -> TableCells.text(BookingStatus::getShortTitle));
    }

    private List<Booking> bookingsOf(Passenger passenger) {
        return bookingsByPassenger.getOrDefault(passenger.getId(), List.of());
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        DocumentType type = documentTypeCombo.getValue();
        filtered.setPredicate(p -> {
            String haystack = String.join(" ", p.getFullName(), p.getDocumentNumber(), p.getEmail(),
                    p.getPhone(), String.valueOf(p.getId())).toLowerCase(Locale.ROOT);
            return haystack.contains(text) && (type == null || p.getDocumentType() == type);
        });
        updateCount();
    }

    private void updateCount() {
        countLabel.setText("Показано " + sorted.size() + " из " + items.size());
    }

    /** Нижняя таблица: брони выбранного пассажира и сводка по ним. */
    private void showBookings(Passenger passenger) {
        if (passenger == null) {
            passengerBookings.clear();
            bookingsTitle.setText("Брони пассажира");
            bookingsSummary.setText("");
            bookingsPlaceholder.setText("Выберите пассажира в таблице, чтобы увидеть его брони");
            return;
        }
        List<Booking> list = bookingsOf(passenger);
        passengerBookings.setAll(list);
        long active = list.stream().filter(b -> b.getStatus().isActive()).count();
        bookingsTitle.setText("Брони пассажира: " + passenger.getShortName());
        bookingsSummary.setText("всего " + list.size() + ", активных " + active);
        bookingsPlaceholder.setText("На этого пассажира броней нет");
    }

    // ---------- кнопки ----------

    /** Перечитать пассажиров и брони из БД (вызывается и при переходе на экран). */
    public void refresh() {
        onRefresh();
    }

    @FXML
    private void onRefresh() {
        Passenger selected = table.getSelectionModel().getSelectedItem();
        reload(selected == null ? null : selected.getId());
    }

    /** Перечитывает данные и выделяет пассажира с указанным ID, если он виден. */
    private void reload(Long selectId) {
        try {
            bookingsByPassenger = bookings.bookingsByPassenger();
            items.setAll(passengers.list());
        } catch (Exception e) {
            error(e);
            return;
        }
        updateCount();
        Passenger toSelect = selectId == null ? null
                : sorted.stream().filter(p -> p.getId() == selectId).findFirst().orElse(null);
        if (toSelect != null) {
            table.getSelectionModel().select(toSelect);
            table.scrollTo(toSelect);
        }
        showBookings(table.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onReset() {
        searchField.clear();
        documentTypeCombo.setValue(null);
        table.getSortOrder().clear();
    }

    @FXML
    private void onAdd() {
        PassengerFormController.open(window(), passengers, null).ifPresent(saved -> reload(saved.getId()));
    }

    @FXML
    private void onEdit() {
        Passenger selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        PassengerFormController.open(window(), passengers, selected).ifPresent(saved -> reload(saved.getId()));
    }

    /** Удаление с подтверждением; правило «нет броней» проверяется до вопроса «удалить?». */
    @FXML
    private void onDelete() {
        Passenger selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            passengers.requireDeletable(selected.getId());
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Удалить пассажира " + selected.getFullName()
                    + "? Действие необратимо.", ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(window());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            passengers.delete(selected.getId());
            reload(null);
        } catch (Exception e) {
            error(e);
        }
    }

    private Window window() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }

    private void error(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
        alert.initOwner(window());
        alert.showAndWait();
    }
}
