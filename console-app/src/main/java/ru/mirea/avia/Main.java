package ru.mirea.avia;

import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.JdbcBookingRepository;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.ui.ConsoleUI;
import ru.mirea.avia.util.DatabaseManager;
import ru.mirea.avia.util.ExcelExporter;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        DatabaseManager database = new DatabaseManager();
        try {
            database.testConnection();

            PassengerRepository passengerRepository = new PassengerRepository(database);
            FlightRepository flightRepository = new FlightRepository(database);
            BookingRepository bookingRepository = new JdbcBookingRepository(database); // полиморфизм

            PassengerService passengerService = new PassengerService(passengerRepository);
            FlightService flightService = new FlightService(flightRepository);
            BookingService bookingService = new BookingService(bookingRepository, passengerRepository, flightRepository);

            new ConsoleUI(passengerService, flightService, bookingService, new ExcelExporter()).run();
        } catch (SQLException e) {
            System.out.println("Не удалось подключиться к базе данных: " + e.getMessage());
        }
    }
}
