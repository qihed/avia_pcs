package ru.mirea.avia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Готовит безопасную памятку с параметрами подключения после успешного старта.
 *
 * <p>Памятка формируется только после подключения к PostgreSQL и применения миграций,
 * как {@code ApplicationReadyEvent} в эталоне: пользователь не получает ложное «готово»,
 * пока схема ещё создаётся. В памятку попадают URL, пользователь PostgreSQL, каталог
 * выгрузки и файл журнала. Пароль в памятку и журнал не выводится.</p>
 */
public final class StartupConnectionInfoLogger {
    private static final Logger log = LoggerFactory.getLogger(StartupConnectionInfoLogger.class);
    private static final Pattern PASSWORD_PARAMETER = Pattern.compile("(?i)([?&][a-z]*password=)[^&]*");
    private static final String BANNER = """

            ============================================================
             AVIA BOOKING CONSOLE IS READY
            ============================================================
             PostgreSQL JDBC:  %s
             PostgreSQL user:  %s
             Export folder:    %s
             Log file:         %s
             Passwords/secrets are not printed.
            ============================================================
            """;

    private final AppProperties properties;

    /**
     * Получает уже проверенную конфигурацию, поэтому памятка совпадает с фактическим подключением.
     *
     * @param properties настройки приложения, загруженные при старте
     */
    public StartupConnectionInfoLogger(AppProperties properties) {
        this.properties = properties;
    }

    /** Возвращает текст памятки и записывает его в журнал. */
    public String banner() {
        String text = BANNER.formatted(
                jdbcUrl(),
                properties.db().user(),
                absolute(properties.export().directory()),
                absolute(Path.of(properties.logFile())));
        log.info("{}", text);
        return text;
    }

    private String jdbcUrl() {
        // Пароль можно передать параметром DB_URL, поэтому его значение в памятке скрывается.
        return PASSWORD_PARAMETER.matcher(properties.db().url()).replaceAll("$1***");
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
