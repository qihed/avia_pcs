package ru.mirea.avia.jdbc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.ErrorCode;

import java.sql.SQLException;
import java.util.Arrays;

/**
 * Применяет миграции схемы {@code avia} при старте приложения.
 *
 * <p>Схемой владеют только миграции {@code db/migration}; демонстрационные данные
 * лежат отдельно в {@code db/demo} и подключаются флагом {@code app.db.demo-data}.
 * Если схему уже создали вручную скриптами {@code sql/schema.sql} и {@code sql/data.sql},
 * Flyway фиксирует её как базовую версию 2 и повторно объекты не создаёт.</p>
 */
public final class MigrationRunner {
    private static final String SCHEMA = "avia";
    private static final String MANUAL_BASELINE_VERSION = "2";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String DEMO_LOCATION = "classpath:db/demo";

    private final DatabaseManager database;
    private final boolean demoData;

    public MigrationRunner(DatabaseManager database, boolean demoData) {
        this.database = database;
        this.demoData = demoData;
    }

    /** Применяет новые миграции и возвращает их число. */
    public int migrate() {
        try {
            return configure(locations()).load().migrate().migrationsExecuted;
        } catch (FlywayException ex) {
            throw translate(ex);
        }
    }

    /**
     * Выбирает каталоги миграций.
     *
     * <p>Демонстрационные данные загружаются только в пустую схему: если оператор уже создал свои
     * записи, версия V2 с фиксированными ID пассажиров и рейсов испортила бы их. Если V2 уже
     * применялась, каталог остаётся подключённым, иначе Flyway отвергнет историю как неполную.</p>
     */
    private String[] locations() {
        MigrationInfo[] applied = configure(MIGRATION_LOCATION).load().info().applied();
        boolean demoApplied = Arrays.stream(applied)
                .anyMatch(info -> info.getVersion() != null
                        && MANUAL_BASELINE_VERSION.equals(info.getVersion().getVersion()));
        return demoApplied || (demoData && applied.length == 0)
                ? new String[]{MIGRATION_LOCATION, DEMO_LOCATION}
                : new String[]{MIGRATION_LOCATION};
    }

    private FluentConfiguration configure(String... locations) {
        return Flyway.configure()
                .dataSource(database.dataSource())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .locations(locations)
                .baselineOnMigrate(true)
                .baselineVersion(MANUAL_BASELINE_VERSION);
    }

    private static DataAccessException translate(FlywayException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return SqlErrors.translate(sql, "Migration failed");
        }
        return new DataAccessException(ErrorCode.E_502, "Migration failed", ex);
    }
}
