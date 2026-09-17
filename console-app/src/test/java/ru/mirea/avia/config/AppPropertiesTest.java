package ru.mirea.avia.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.avia.config.AppProperties.Db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет подстановку переменных окружения и проверку параметров запуска (NFR-11). */
class AppPropertiesTest {
    private final Properties raw = defaults();
    private final Map<String, String> variables = new HashMap<>();

    @TempDir
    Path directory;

    @Test
    void usesDefaultsWhenVariablesAreMissing() {
        AppProperties properties = load();

        assertThat(properties.db().url()).isEqualTo("jdbc:postgresql://localhost:5432/avia_booking?currentSchema=avia");
        assertThat(properties.db().user()).isEqualTo("avia_app");
        assertThat(properties.db().password()).isEmpty();
        assertThat(properties.db().connectTimeoutSeconds()).isEqualTo(5);
        assertThat(properties.db().demoData()).isTrue();
        assertThat(properties.booking().saleCloseBefore()).isEqualTo(Duration.ofMinutes(60));
        assertThat(properties.booking().cancelNotLaterThan()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.booking().expireAfter()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.export().directory()).isEqualTo(Path.of("exports"));
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
        assertThat(properties.logFile()).isEqualTo("avia-booking.log");
    }

    @Test
    void resolvesNestedPlaceholders() {
        variables.put("POSTGRES_PORT", "5544");
        variables.put("POSTGRES_DB", "avia_test");
        variables.put("POSTGRES_USER", "avia_owner");
        variables.put("POSTGRES_PASSWORD", "Compose-Secret-2026");

        AppProperties properties = load();

        assertThat(properties.db().url()).isEqualTo("jdbc:postgresql://localhost:5544/avia_test?currentSchema=avia");
        assertThat(properties.db().user()).isEqualTo("avia_owner");
        assertThat(properties.db().password()).isEqualTo("Compose-Secret-2026");
    }

    @Test
    void outerVariableTakesPrecedenceOverNestedOnes() {
        variables.put("DB_URL", "jdbc:postgresql://db.example.org:6432/avia?currentSchema=avia");
        variables.put("POSTGRES_PORT", "5544");
        variables.put("DB_USER", "operator");
        variables.put("POSTGRES_USER", "avia_owner");
        variables.put("DB_PASSWORD", "Operator-Secret-2026");
        variables.put("POSTGRES_PASSWORD", "Compose-Secret-2026");

        AppProperties properties = load();

        assertThat(properties.db().url()).isEqualTo("jdbc:postgresql://db.example.org:6432/avia?currentSchema=avia");
        assertThat(properties.db().user()).isEqualTo("operator");
        assertThat(properties.db().password()).isEqualTo("Operator-Secret-2026");
    }

    @Test
    void readsOptionalSettingsFromVariables() {
        variables.put("DEMO_DATA", "no");
        variables.put("EXPORT_DIR", "/var/lib/avia/exports");
        variables.put("APP_TIME_ZONE", "Asia/Yekaterinburg");

        AppProperties properties = load();

        assertThat(properties.db().demoData()).isFalse();
        assertThat(properties.export().directory()).isEqualTo(Path.of("/var/lib/avia/exports"));
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Asia/Yekaterinburg"));
    }

    @Test
    void resolvesSeveralPlaceholdersInOneValue() {
        assertThat(AppProperties.resolve("  ${HOST:localhost}:${PORT:5432}  ", variables::get))
                .isEqualTo("localhost:5432");
        assertThat(AppProperties.resolve("${HOST:localhost}:${PORT:5432}", name -> "db"))
                .isEqualTo("db:db");
        assertThat(AppProperties.resolve("${lower_case:kept}", variables::get)).isEqualTo("${lower_case:kept}");
        assertThat(AppProperties.resolve(null, variables::get)).isEmpty();
    }

    @Test
    void keepsVariableValuesLiterally() {
        variables.put("DB_PASSWORD", "Pa${HOME:x}ss-2026");

        assertThat(load().db().password()).isEqualTo("Pa${HOME:x}ss-2026");
    }

    @Test
    void rejectsNonPostgresJdbcUrl() {
        variables.put("DB_URL", "jdbc:mysql://localhost:3306/avia");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    void rejectsBlankDatabaseUser() {
        variables.put("DB_USER", "   ");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USER");
    }

    @Test
    void hidesPasswordInToString() {
        Db db = new Db("jdbc:postgresql://localhost:5432/avia_booking", "avia_app", "Top-Secret-2026", 5, true);
        variables.put("DB_PASSWORD", "Top-Secret-2026");

        assertThat(db.toString()).contains("password=***").doesNotContain("Top-Secret-2026");
        assertThat(load().toString()).doesNotContain("Top-Secret-2026");
    }

    @Test
    void rejectsNonNumericTimeout() {
        raw.setProperty("app.db.connect-timeout-seconds", "five");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.db.connect-timeout-seconds")
                .hasMessageContaining("five");
    }

    @Test
    void rejectsNonPositiveBookingLimit() {
        raw.setProperty("app.booking.expire-minutes", "0");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.booking.expire-minutes must be a positive integer");
    }

    @Test
    void rejectsMissingBookingLimit() {
        raw.remove("app.booking.cancel-hours");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.booking.cancel-hours must not be blank");
    }

    @Test
    void rejectsUnknownTimeZone() {
        variables.put("APP_TIME_ZONE", "Mars/Olympus");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TIME_ZONE")
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    void rejectsMalformedTimeZone() {
        variables.put("APP_TIME_ZONE", "UTC+99:00");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TIME_ZONE");
    }

    @Test
    void rejectsUnknownDemoDataFlag() {
        variables.put("DEMO_DATA", "maybe");

        assertThatThrownBy(this::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.db.demo-data");
    }

    @Test
    void readsDotEnvWithQuotesAndComments() throws IOException {
        Path file = directory.resolve(".env");
        Files.writeString(file, """
                # Локальный запуск: пароль не хранится в application.properties
                #DB_URL=jdbc:mysql://localhost:3306/avia
                DB_USER=avia_app
                DB_PASSWORD="Pa ss=word #1"
                EXPORT_DIR='exports/отчёты'
                  POSTGRES_PORT = 5544\s\s

                =orphan
                BROKEN LINE
                DEMO_DATA=
                APP_TIME_ZONE="Europe/Moscow'
                """);

        Map<String, String> values = AppProperties.readDotEnv(file);

        assertThat(values).containsOnly(
                Map.entry("DB_USER", "avia_app"),
                Map.entry("DB_PASSWORD", "Pa ss=word #1"),
                Map.entry("EXPORT_DIR", "exports/отчёты"),
                Map.entry("POSTGRES_PORT", "5544"),
                Map.entry("DEMO_DATA", ""),
                Map.entry("APP_TIME_ZONE", "\"Europe/Moscow'"));
    }

    @Test
    void dotEnvValuesFeedPlaceholders() throws IOException {
        Path file = directory.resolve(".env");
        Files.writeString(file, "POSTGRES_PASSWORD='Compose-Secret-2026'\nPOSTGRES_DB=avia_demo\n");
        Map<String, String> values = AppProperties.readDotEnv(file);

        AppProperties properties = AppProperties.load(raw, values::get);

        assertThat(properties.db().password()).isEqualTo("Compose-Secret-2026");
        assertThat(properties.db().url()).isEqualTo("jdbc:postgresql://localhost:5432/avia_demo?currentSchema=avia");
    }

    @Test
    void returnsEmptyMapWithoutDotEnv() {
        assertThat(AppProperties.readDotEnv(directory.resolve(".env"))).isEmpty();
        assertThat(AppProperties.readDotEnv(directory)).isEmpty();
    }

    private AppProperties load() {
        return AppProperties.load(raw, variables::get);
    }

    private static Properties defaults() {
        Properties raw = new Properties();
        raw.setProperty("app.db.url", "${DB_URL:jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/"
                + "${POSTGRES_DB:avia_booking}?currentSchema=avia}");
        raw.setProperty("app.db.user", "${DB_USER:${POSTGRES_USER:avia_app}}");
        raw.setProperty("app.db.password", "${DB_PASSWORD:${POSTGRES_PASSWORD:}}");
        raw.setProperty("app.db.connect-timeout-seconds", "5");
        raw.setProperty("app.db.demo-data", "${DEMO_DATA:true}");
        raw.setProperty("app.booking.sale-close-minutes", "60");
        raw.setProperty("app.booking.cancel-hours", "2");
        raw.setProperty("app.booking.expire-minutes", "30");
        raw.setProperty("app.export.directory", "${EXPORT_DIR:exports}");
        raw.setProperty("app.time-zone", "${APP_TIME_ZONE:Europe/Moscow}");
        raw.setProperty("app.log-file", "avia-booking.log");
        return raw;
    }
}
