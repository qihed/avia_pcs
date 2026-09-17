package ru.mirea.avia.repository.impl;

import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.jdbc.SqlErrors;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared JDBC plumbing of the repositories.
 *
 * <p>The connection of the current transaction is taken from {@link DatabaseManager#connection()}
 * and is never closed here: the manager owns it (DB-06, DB-07). Every query is a
 * {@link PreparedStatement} with {@code ?} parameters (DB-03, NFR-10), statements and result sets
 * are closed by try-with-resources (DB-04, NFR-08), and {@link SQLException} never leaves the
 * layer: it is translated by {@link SqlErrors} (DB-05).</p>
 */
public abstract class AbstractJdbcRepository {
    private final DatabaseManager database;

    protected AbstractJdbcRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Runs a SELECT and hands the whole result set to {@code extractor}. */
    protected <R> R query(String sql, ResultSetExtractor<R> extractor, String context, Object... params) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                return extractor.extract(rs);
            }
        } catch (SQLException ex) {
            throw SqlErrors.translate(ex, context);
        }
    }

    /** Runs a SELECT and maps every row with {@code mapper}. */
    protected <R> List<R> queryForList(String sql, RowMapper<R> mapper, String context, Object... params) {
        return query(sql, rs -> {
            List<R> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapper.map(rs));
            }
            return result;
        }, context, params);
    }

    /** Runs a SELECT and maps the first row, if any. */
    protected <R> Optional<R> queryForObject(String sql, RowMapper<R> mapper, String context, Object... params) {
        List<R> rows = queryForList(sql, mapper, context, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Runs a single-value SELECT such as {@code count(*)}; an empty result counts as zero. */
    protected long queryForLong(String sql, String context, Object... params) {
        return queryForObject(sql, rs -> rs.getLong(1), context, params).orElse(0L);
    }

    /** Runs INSERT, UPDATE or DELETE and returns the number of affected rows. */
    protected int executeUpdate(String sql, String context, Object... params) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw SqlErrors.translate(ex, context);
        }
    }

    /**
     * Runs an INSERT and returns the generated {@code id}.
     *
     * <p>The PostgreSQL driver appends {@code RETURNING id} to the statement; the same code reads
     * an AUTO_INCREMENT value on MySQL (NFR-21).</p>
     */
    protected long insertAndGetId(String sql, String context, Object... params) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql, new String[]{"id"})) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Database did not return a generated key");
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw SqlErrors.translate(ex, context);
        }
    }

    /** Binds parameters with the typed {@code setXxx} methods; enums are stored by name. */
    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;
            switch (params[i]) {
                case null -> statement.setNull(index, Types.VARCHAR);
                case String value -> statement.setString(index, value);
                case Long value -> statement.setLong(index, value);
                case Integer value -> statement.setInt(index, value);
                case BigDecimal value -> statement.setBigDecimal(index, value);
                case Boolean value -> statement.setBoolean(index, value);
                case LocalDateTime value -> statement.setTimestamp(index, Timestamp.valueOf(value));
                case LocalDate value -> statement.setDate(index, Date.valueOf(value));
                case Enum<?> value -> statement.setString(index, value.name());
                default -> throw new IllegalArgumentException(
                        "Unsupported parameter type: " + params[i].getClass().getName());
            }
        }
    }

    protected static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    protected static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    /** Maps the current row of a result set to an object. */
    @FunctionalInterface
    protected interface RowMapper<R> {
        R map(ResultSet rs) throws SQLException;
    }

    /** Reads a whole result set, including its metadata. */
    @FunctionalInterface
    protected interface ResultSetExtractor<R> {
        R extract(ResultSet rs) throws SQLException;
    }
}
