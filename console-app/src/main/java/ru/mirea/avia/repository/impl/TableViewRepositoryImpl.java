package ru.mirea.avia.repository.impl;

import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.dto.TableDtos.DatabaseTable;
import ru.mirea.avia.dto.TableDtos.TablePage;
import ru.mirea.avia.jdbc.DatabaseManager;
import ru.mirea.avia.repository.TableViewRepository;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence gateway for raw paged table output (FR-18).
 *
 * <p>A table name cannot be a {@code PreparedStatement} parameter, so the query text is picked
 * from a fixed set by the enum value and user input never reaches SQL.</p>
 */
public final class TableViewRepositoryImpl extends AbstractJdbcRepository implements TableViewRepository {
    private static final String SELECT_PASSENGERS = "SELECT * FROM passengers ORDER BY id LIMIT ? OFFSET ?";
    private static final String SELECT_FLIGHTS = "SELECT * FROM flights ORDER BY id LIMIT ? OFFSET ?";
    private static final String SELECT_BOOKINGS = "SELECT * FROM bookings ORDER BY id LIMIT ? OFFSET ?";
    private static final String COUNT_PASSENGERS = "SELECT count(*) FROM passengers";
    private static final String COUNT_FLIGHTS = "SELECT count(*) FROM flights";
    private static final String COUNT_BOOKINGS = "SELECT count(*) FROM bookings";
    private static final String NULL_VALUE = "NULL";

    public TableViewRepositoryImpl(DatabaseManager database) {
        super(database);
    }

    @Override
    public TablePage findPage(DatabaseTable table, int page, int size) {
        String name = tableName(table);
        long total = queryForLong(countSql(table), "Failed to count rows of table " + name);
        return query(selectSql(table), rs -> {
            List<String> columns = columns(rs.getMetaData());
            List<List<String>> rows = rows(rs, columns.size());
            return new TablePage(table, columns, PageResponse.from(rows, page, size, total));
        }, "Failed to read table " + name, size, page * size);
    }

    private static List<String> columns(ResultSetMetaData meta) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        return List.copyOf(columns);
    }

    private static List<List<String>> rows(ResultSet rs, int columnCount) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                row.add(value == null ? NULL_VALUE : value);
            }
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    private static String selectSql(DatabaseTable table) {
        return switch (table) {
            case PASSENGERS -> SELECT_PASSENGERS;
            case FLIGHTS -> SELECT_FLIGHTS;
            case BOOKINGS -> SELECT_BOOKINGS;
        };
    }

    private static String countSql(DatabaseTable table) {
        return switch (table) {
            case PASSENGERS -> COUNT_PASSENGERS;
            case FLIGHTS -> COUNT_FLIGHTS;
            case BOOKINGS -> COUNT_BOOKINGS;
        };
    }

    private static String tableName(DatabaseTable table) {
        return switch (table) {
            case PASSENGERS -> "passengers";
            case FLIGHTS -> "flights";
            case BOOKINGS -> "bookings";
        };
    }
}
