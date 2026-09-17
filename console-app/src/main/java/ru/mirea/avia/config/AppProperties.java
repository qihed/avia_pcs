package ru.mirea.avia.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Типобезопасная конфигурация консольного приложения.
 *
 * <p>Значения читаются один раз при старте из {@code application.properties}. Подстановка
 * {@code ${VAR:default}} берёт значение по порядку: системное свойство {@code -DVAR}, переменная
 * окружения, файл {@code .env} в каталоге запуска, значение по умолчанию. Сервисы получают этот
 * record и не обращаются к {@code System.getenv} напрямую (NFR-11).</p>
 */
public record AppProperties(
        Db db,
        BookingRules booking,
        Export export,
        ZoneId timeZone,
        String logFile) {

    private static final String RESOURCE = "application.properties";
    private static final Path DOT_ENV = Path.of(".env");
    private static final Pattern VARIABLE = Pattern.compile("[A-Z0-9_]+");

    /** Параметры подключения к PostgreSQL. */
    public record Db(String url, String user, String password, int connectTimeoutSeconds, boolean demoData) {
        public Db {
            if (url == null || !url.startsWith("jdbc:postgresql://")) {
                throw new IllegalStateException("DB_URL must start with jdbc:postgresql://");
            }
            if (user == null || user.isBlank()) throw new IllegalStateException("DB_USER must not be blank");
            if (connectTimeoutSeconds < 1) {
                throw new IllegalStateException("app.db.connect-timeout-seconds must be positive");
            }
        }

        /** Скрывает пароль при случайном выводе record в журнал. */
        @Override
        public String toString() {
            return "Db[url=" + url + ", user=" + user + ", password=***, demoData=" + demoData + "]";
        }
    }

    /** Сроки бизнес-правил BR-01, BR-08 и BR-09. */
    public record BookingRules(Duration saleCloseBefore, Duration cancelNotLaterThan, Duration expireAfter) {
    }

    /** Каталог выгрузки отчётов. */
    public record Export(Path directory) {
    }

    /** Загружает конфигурацию из classpath, окружения и файла {@code .env}. */
    public static AppProperties load() {
        Map<String, String> dotEnv = readDotEnv(DOT_ENV);
        return load(readResource(), name -> lookup(name, dotEnv));
    }

    /**
     * Собирает конфигурацию из готового набора свойств.
     *
     * @param raw    свойства с плейсхолдерами {@code ${VAR:default}}
     * @param lookup источник значений переменных; {@code null} означает «использовать значение по умолчанию»
     * @return проверенная конфигурация
     */
    public static AppProperties load(Properties raw, UnaryOperator<String> lookup) {
        Map<String, String> values = new HashMap<>();
        for (String key : raw.stringPropertyNames()) {
            values.put(key, resolve(raw.getProperty(key), lookup));
        }
        Db db = new Db(
                values.get("app.db.url"),
                values.get("app.db.user"),
                values.getOrDefault("app.db.password", ""),
                integer(values, "app.db.connect-timeout-seconds"),
                bool(values, "app.db.demo-data"));
        BookingRules booking = new BookingRules(
                Duration.ofMinutes(integer(values, "app.booking.sale-close-minutes")),
                Duration.ofHours(integer(values, "app.booking.cancel-hours")),
                Duration.ofMinutes(integer(values, "app.booking.expire-minutes")));
        Export export = new Export(directory(values));
        return new AppProperties(db, booking, export, zone(values), required(values, "app.log-file"));
    }

    /**
     * Подставляет значения в шаблон {@code ${VAR:default}}; значение по умолчанию может само содержать шаблон.
     *
     * <p>Раскрывается только текст шаблона: значение переменной подставляется как есть, поэтому пароль
     * с символами {@code ${…}} не искажается, а переменная, ссылающаяся на себя, не зацикливает старт.</p>
     */
    static String resolve(String raw, UnaryOperator<String> lookup) {
        return substitute(raw == null ? "" : raw.trim(), lookup);
    }

    /**
     * Читает файл {@code KEY=VALUE}; комментарии и пустые строки пропускаются.
     *
     * @param path путь к файлу {@code .env}
     * @return значения переменных или пустая карта, если файла нет
     */
    static Map<String, String> readDotEnv(Path path) {
        if (!Files.isRegularFile(path)) return Map.of();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<String, String> result = new HashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                int separator = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || separator < 1) continue;
                result.put(trimmed.substring(0, separator).trim(), value(trimmed.substring(separator + 1).trim()));
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path.toAbsolutePath(), ex);
        }
    }

    /** Значение переменной: кавычки снимаются, комментарий в конце строки отбрасывается. */
    private static String substitute(String template, UnaryOperator<String> lookup) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < template.length()) {
            if (!template.startsWith("${", index)) {
                result.append(template.charAt(index++));
                continue;
            }
            int end = closingBrace(template, index + 2);
            result.append(expand(template.substring(index + 2, end), lookup));
            index = end + 1;
        }
        return result.toString();
    }

    private static String expand(String body, UnaryOperator<String> lookup) {
        int separator = body.indexOf(':');
        String name = separator < 0 ? body : body.substring(0, separator);
        // Не шаблон переменной (например, строчные буквы) — текст остаётся как есть.
        if (!VARIABLE.matcher(name).matches()) return "${" + body + "}";
        String value = lookup.apply(name);
        if (value != null) return value;
        return separator < 0 ? "" : substitute(body.substring(separator + 1), lookup);
    }

    private static int closingBrace(String template, int from) {
        int depth = 1;
        for (int index = from; index < template.length(); index++) {
            if (template.startsWith("${", index)) {
                depth++;
                index++;
            } else if (template.charAt(index) == '}' && --depth == 0) {
                return index;
            }
        }
        throw new IllegalStateException("Unclosed placeholder in application.properties: " + template);
    }

    private static String lookup(String name, Map<String, String> dotEnv) {
        String property = System.getProperty(name);
        if (property != null) return property;
        String env = System.getenv(name);
        if (env != null) return env;
        return dotEnv.get(name);
    }

    private static Properties readResource() {
        try (InputStream input = AppProperties.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException(RESOURCE + " is missing on the classpath");
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + RESOURCE, ex);
        }
    }

    private static String value(String raw) {
        String unquoted = stripQuotes(raw);
        if (!unquoted.equals(raw)) return unquoted;
        int comment = raw.indexOf(" #");
        return comment < 0 ? raw : raw.substring(0, comment).trim();
    }

    private static String stripQuotes(String value) {
        boolean quoted = value.length() >= 2 && value.charAt(0) == value.charAt(value.length() - 1)
                && (value.charAt(0) == '"' || value.charAt(0) == '\'');
        return quoted ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must not be blank");
        return value;
    }

    private static int integer(Map<String, String> values, String key) {
        String value = required(values, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) throw new NumberFormatException(value);
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(key + " must be a positive integer: " + value, ex);
        }
    }

    private static boolean bool(Map<String, String> values, String key) {
        return switch (required(values, key).toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalStateException(key + " must be true or false");
        };
    }

    private static Path directory(Map<String, String> values) {
        String value = required(values, "app.export.directory");
        try {
            return Path.of(value);
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("EXPORT_DIR is not a valid path: " + value, ex);
        }
    }

    private static ZoneId zone(Map<String, String> values) {
        String value = required(values, "app.time-zone");
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("APP_TIME_ZONE is not a valid time zone: " + value, ex);
        }
    }
}
