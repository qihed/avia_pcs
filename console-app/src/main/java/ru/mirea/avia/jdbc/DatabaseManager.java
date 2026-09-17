package ru.mirea.avia.jdbc;

import org.postgresql.ds.PGSimpleDataSource;
import ru.mirea.avia.config.AppProperties.Db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Единственная точка доступа к PostgreSQL (DB-06) и граница транзакций (DB-07).
 *
 * <p>Соединение открывается на время одной транзакции и привязывается к потоку, поэтому
 * репозитории получают его через {@link #connection()} и не принимают {@code Connection}
 * параметром. Соединение закрывается try-with-resources сразу после commit или rollback
 * (NFR-08): между командами меню открытых соединений нет. Пул не нужен — приложение
 * однопользовательское (CON-05).</p>
 */
public final class DatabaseManager implements Transactions {
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public DatabaseManager(Db db) {
        dataSource.setURL(db.url());
        dataSource.setUser(db.user());
        dataSource.setPassword(db.password());
        dataSource.setConnectTimeout(db.connectTimeoutSeconds());
        dataSource.setLoginTimeout(db.connectTimeoutSeconds());
        dataSource.setApplicationName("avia-booking-console");
        // Значения ключей (номера документов, e-mail) не попадают в тексты ошибок сервера — NFR-12.
        dataSource.setLogServerErrorDetail(false);
    }

    /** Проверяет, что СУБД доступна (FR-22, NFR-07); при ошибке бросает E-501. */
    public void checkConnection() {
        try (Connection connection = openConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new SQLException("Connection is not valid", "08006");
            }
        } catch (SQLException ex) {
            throw SqlErrors.translate(ex, "Database is unavailable");
        }
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return execute(work, true);
    }

    @Override
    public <T> T write(Supplier<T> work) {
        return execute(work, false);
    }

    /** Возвращает соединение открытой транзакции. */
    public Connection connection() {
        Connection connection = current.get();
        if (connection == null) throw new IllegalStateException("No active transaction");
        return connection;
    }

    /** Источник соединений для миграций Flyway. */
    public DataSource dataSource() {
        return dataSource;
    }

    /** Отвязывает соединение от потока при выходе из программы (FR-23). */
    public void close() {
        current.remove();
    }

    private <T> T execute(Supplier<T> work, boolean readOnly) {
        if (current.get() != null) return work.get();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(readOnly);
            current.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error ex) {
                rollback(connection, ex);
                throw ex;
            } finally {
                current.remove();
            }
        } catch (SQLException ex) {
            throw SqlErrors.translate(ex, "Transaction failed");
        }
    }

    private Connection openConnection() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            // Любой отказ при открытии соединения — это недоступность СУБД (E-501), а не ошибка запроса.
            throw new SQLException(ex.getMessage(), "08001", ex);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            failure.addSuppressed(ex);
        }
    }
}
