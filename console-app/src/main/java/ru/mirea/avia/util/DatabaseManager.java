package ru.mirea.avia.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Подключение к PostgreSQL. Параметры берутся из переменных окружения DB_URL, DB_USER, DB_PASSWORD
 * (их выставляет лаунчер ./avia), а если их нет — из файла database.properties. Пароля в коде нет.
 */
public class DatabaseManager {
    private final Properties file = new Properties();
    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager() {
        try (InputStream in = DatabaseManager.class.getResourceAsStream("/database.properties")) {
            if (in != null) file.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать database.properties", e);
        }
        url = setting("DB_URL", "db.url");
        user = setting("DB_USER", "db.user");
        password = setting("DB_PASSWORD", "db.password");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void testConnection() throws SQLException {
        getConnection().close();
    }

    public String getUrl() {
        return url;
    }

    private String setting(String envName, String fileKey) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) value = file.getProperty(fileKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр подключения к БД: переменная " + envName
                    + " или ключ " + fileKey + " в database.properties");
        }
        return value.trim();
    }
}
