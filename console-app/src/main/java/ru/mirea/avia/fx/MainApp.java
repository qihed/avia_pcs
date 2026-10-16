package ru.mirea.avia.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import ru.mirea.avia.fx.controller.BookingsController;
import ru.mirea.avia.fx.controller.MainController;
import ru.mirea.avia.fx.controller.PassengerViewController;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.JdbcBookingRepository;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.DatabaseManager;
import ru.mirea.avia.util.ExcelExporter;

/** JavaFX-приложение: проверяет подключение к БД, собирает слои (как в консольном Main) и открывает окно. */
public final class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseManager database;
        try {
            database = new DatabaseManager();
            database.testConnection();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Не удалось подключиться к базе данных.\n" + e.getMessage()
                    + "\n\nЕсли БД в Docker, запустите её: ./avia up docker").showAndWait();
            Platform.exit();
            return;
        }

        PassengerRepository passengers = new PassengerRepository(database);
        FlightRepository flights = new FlightRepository(database);
        BookingRepository bookingRepository = new JdbcBookingRepository(database);
        BookingService bookings = new BookingService(bookingRepository, passengers, flights);
        PassengerService passengerService = new PassengerService(passengers);
        FlightService flightService = new FlightService(flights);

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = mainLoader.load();
        FXMLLoader bookingsLoader = new FXMLLoader(getClass().getResource("/fxml/BookingsView.fxml"));
        BookingsController bookingsController = new BookingsController(bookings, passengerService, new ExcelExporter());
        bookingsLoader.setController(bookingsController);
        FXMLLoader passengersLoader = new FXMLLoader(getClass().getResource("/fxml/PassengerView.fxml"));
        PassengerViewController passengersController = new PassengerViewController(passengerService, bookings);
        passengersLoader.setController(passengersController);

        String env = System.getenv("AVIA_ENV");
        String target = database.getUrl().replace("jdbc:postgresql://", "");
        MainController main = mainLoader.getController();
        main.setConnection((env == null ? "ручной запуск" : env) + " · " + target);
        // Экраны меню; при переходе на экран его данные перечитываются из БД.
        main.addScreen("Бронирования", bookingsLoader.load(), bookingsController::refresh);
        main.addScreen("Пассажиры", passengersLoader.load(), passengersController::refresh);
        // Формы рейса (пока нет экрана «Рейсы»); после сохранения таблица броней перечитывается.
        main.setForms(flightService, bookingsController::refresh);

        Scene scene = new Scene(root, 1360, 820);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("Система бронирования авиабилетов");
        stage.setMinWidth(1300);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }
}
