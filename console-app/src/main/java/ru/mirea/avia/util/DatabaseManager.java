package ru.mirea.avia.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Подключение к PostgreSQL. Настройки — константы по умолчанию с возможностью переопределить через переменные окружения. */
public class DatabaseManager {
    private final String url = env("DB_URL", "jdbc:postgresql://localhost:5432/avia_booking");
    private final String user = env("DB_USER", "avia_app");
    private final String password = env("DB_PASSWORD", "avia_app_password");

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void testConnection() throws SQLException {
        try (Connection ignored = getConnection()) {
            // Соединение успешно открыто и будет закрыто автоматически.
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
