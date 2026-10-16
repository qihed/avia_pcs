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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import ru.mirea.avia.model.Booking;
import ru.mirea.avia.model.BookingStatus;
import ru.mirea.avia.model.FareClass;
import ru.mirea.avia.model.Flight;
import ru.mirea.avia.model.Passenger;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.ExcelExporter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Экран «Бронирования»: таблица с поиском и фильтрами, сортировка кликом по заголовку колонки,
 * панель данных пассажира и рейса, добавление и изменение через форму брони, удаление и экспорт в Excel.
 * SQL здесь нет — только вызовы BookingService.
 */
public final class BookingsController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML private TextField searchField;
    @FXML private ComboBox<BookingStatus> statusCombo;
    @FXML private ComboBox<FareClass> fareClassCombo;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private Button editButton;
    @FXML private Button statusButton;
    @FXML private Button deleteButton;

    @FXML private TableView<Booking> table;
    @FXML private TableColumn<Booking, Long> idColumn;
    @FXML private TableColumn<Booking, String> passengerColumn;
    @FXML private TableColumn<Booking, String> flightColumn;
    @FXML private TableColumn<Booking, String> routeColumn;
    @FXML private TableColumn<Booking, LocalDateTime> departureColumn;
    @FXML private TableColumn<Booking, String> seatColumn;
    @FXML private TableColumn<Booking, FareClass> fareClassColumn;
    @FXML private TableColumn<Booking, BigDecimal> priceColumn;
    @FXML private TableColumn<Booking, BookingStatus> statusColumn;
    @FXML private Label countLabel;

    @FXML private Label placeholder;
    @FXML private VBox detailsBox;
    @FXML private Label bookingInfo;
    @FXML private Label passengerInfo;
    @FXML private Label flightInfo;

    private final BookingService bookings;
    private final PassengerService passengers;
    private final ExcelExporter exporter;
    private final ObservableList<Booking> items = FXCollections.observableArrayList();
    private final FilteredList<Booking> filtered = new FilteredList<>(items);
    private final SortedList<Booking> sorted = new SortedList<>(filtered);

    public BookingsController(BookingService bookings, PassengerService passengers, ExcelExporter exporter) {
        this.bookings = bookings;
        this.passengers = passengers;
        this.exporter = exporter;
    }

    @FXML
    private void initialize() {
        statusCombo.getItems().setAll(BookingStatus.values());
        statusCombo.setConverter(titles(BookingStatus::getTitle));
        fareClassCombo.getItems().setAll(FareClass.values());
        fareClassCombo.setConverter(titles(FareClass::getTitle));

        // Любое изменение фильтра пересчитывает видимые строки.
        searchField.textProperty().addListener((o, a, b) -> applyFilter());
        statusCombo.valueProperty().addListener((o, a, b) -> applyFilter());
        fareClassCombo.valueProperty().addListener((o, a, b) -> applyFilter());
        fromPicker.valueProperty().addListener((o, a, b) -> applyFilter());
        toPicker.valueProperty().addListener((o, a, b) -> applyFilter());

        setupColumns();
        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());   // сортировка кликом по заголовку
        sorted.addListener((ListChangeListener<Booking>) c -> updateCount());

        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        statusButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        detailsBox.visibleProperty().bind(noSelection.not());
        detailsBox.managedProperty().bind(detailsBox.visibleProperty());
        placeholder.visibleProperty().bind(noSelection);
        placeholder.managedProperty().bind(placeholder.visibleProperty());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, booking) -> showDetails(booking));
        // Двойной клик по строке — то же, что кнопка «Изменить».
        table.setRowFactory(t -> {
            TableRow<Booking> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) onEdit();
            });
            return row;
        });

        onRefresh();
    }

    private void setupColumns() {
        idColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getId()));
        passengerColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPassenger().getShortName()));
        flightColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFlight().getFlightNumber()));
        routeColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFlight().getRoute()));
        departureColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFlight().getDepartureTime()));
        seatColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getSeatNumber()));
        fareClassColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFareClass()));
        priceColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPrice()));
        statusColumn.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getStatus()));

        // Значения в колонках типизированы (дата, сумма), поэтому сортируются по значению, а не как текст.
        departureColumn.setCellFactory(c -> textCell(DATE_TIME::format));
        fareClassColumn.setCellFactory(c -> textCell(FareClass::getTitle));
        priceColumn.setCellFactory(c -> textCell(BigDecimal::toPlainString));
        statusColumn.setCellFactory(c -> textCell(BookingStatus::getShortTitle));
    }

    private static <T> StringConverter<T> titles(Function<T, String> title) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : title.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static <T> TableCell<Booking, T> textCell(Function<T, String> format) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
    }

    private void applyFilter() {
        String text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        BookingStatus status = statusCombo.getValue();
        FareClass fareClass = fareClassCombo.getValue();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        filtered.setPredicate(b -> {
            Flight flight = b.getFlight();
            LocalDate departure = flight.getDepartureTime().toLocalDate();
            String haystack = String.join(" ", b.getPassenger().getFullName(), flight.getFlightNumber(),
                    flight.getRoute(), b.getSeatNumber(), String.valueOf(b.getId())).toLowerCase(Locale.ROOT);
            return haystack.contains(text)
                    && (status == null || b.getStatus() == status)
                    && (fareClass == null || b.getFareClass() == fareClass)
                    && (from == null || !departure.isBefore(from))
                    && (to == null || !departure.isAfter(to));
        });
        updateCount();
    }

    private void updateCount() {
        countLabel.setText("Показано " + sorted.size() + " из " + items.size());
    }

    private void showDetails(Booking booking) {
        if (booking == null) return;
        Passenger p = booking.getPassenger();
        Flight f = booking.getFlight();
        bookingInfo.setText("Бронь №" + booking.getId() + " · " + booking.getStatus().getTitle());
        passengerInfo.setText(p.getFullName() + "\n" + p.getDocumentType().getTitle() + " " + p.getDocumentNumber()
                + "\nРождение: " + p.getBirthDate() + " (" + p.getAge() + " лет)"
                + "\nE-mail: " + p.getEmail() + "\nТелефон: " + p.getPhone());
        flightInfo.setText(f.getFlightNumber() + " · " + f.getAirline() + "\n" + f.getRoute()
                + "\nВылет: " + DATE_TIME.format(f.getDepartureTime())
                + "\nПрилёт: " + DATE_TIME.format(f.getArrivalTime())
                + "\nМесто " + booking.getSeatNumber() + ", " + booking.getFareClass().getTitle()
                + ", " + booking.getPrice() + " руб.");
    }

    // ---------- кнопки ----------

    /** Перечитать брони из БД (например, после изменения пассажира или рейса в форме). */
    public void refresh() {
        onRefresh();
    }

    @FXML
    private void onRefresh() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        reload(selected == null ? null : selected.getId());
    }

    /** Перечитывает брони и выделяет строку с указанным ID, если она видна. */
    private void reload(Long selectId) {
        try {
            items.setAll(bookings.list());
        } catch (Exception e) {
            error(e);
            return;
        }
        updateCount();
        if (selectId != null) {
            sorted.stream().filter(b -> b.getId() == selectId).findFirst().ifPresent(b -> {
                table.getSelectionModel().select(b);
                table.scrollTo(b);
            });
        }
    }

    @FXML
    private void onReset() {
        searchField.clear();
        statusCombo.setValue(null);
        fareClassCombo.setValue(null);
        fromPicker.setValue(null);
        toPicker.setValue(null);
        table.getSortOrder().clear();
    }

    @FXML
    private void onAdd() {
        BookingFormController.open(table.getScene().getWindow(), bookings, passengers, null)
                .ifPresent(saved -> reload(saved.getId()));
    }

    /** Изменение: сначала проверяется, что бронь можно менять, и только потом открывается форма. */
    @FXML
    private void onEdit() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Booking current;
        try {
            current = bookings.requireEditable(selected.getId());   // CREATED/PAID и рейс ещё не вылетел
        } catch (Exception e) {
            error(e);
            return;
        }
        BookingFormController.open(table.getScene().getWindow(), bookings, passengers, current)
                .ifPresent(saved -> reload(saved.getId()));
    }

    /** Смена статуса пока в разработке. */
    @FXML
    private void onChangeStatus() {
        new Alert(Alert.AlertType.INFORMATION, "Смена статуса пока в разработке.").showAndWait();
    }

    @FXML
    private void onDelete() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        try {
            bookings.requireDeletable(selected.getId());   // правило «удалять можно только отменённую/просроченную»
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Удалить бронь №" + selected.getId()
                    + "? Действие необратимо.", ButtonType.OK, ButtonType.CANCEL);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            bookings.delete(selected.getId());
            onRefresh();
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить бронирования в Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("bookings_" + LocalDate.now() + ".xlsx");
        File file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;
        try {
            exporter.export(List.copyOf(sorted), file.toPath());   // выгружается то, что сейчас видно в таблице
        } catch (Exception e) {
            error(e);
        }
    }

    private void error(Exception e) {
        new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
    }
}
