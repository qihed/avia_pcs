package ru.mirea.avia.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.mirea.avia.dto.AnalyticsResponse;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;
    public AnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AnalyticsResponse getSummary() {
        long total = count("SELECT count(*) FROM bookings");
        long active = count("SELECT count(*) FROM bookings WHERE archived = false");
        long archived = count("SELECT count(*) FROM bookings WHERE archived = true");
        long passengers = count("SELECT count(*) FROM passengers");
        long flights = count("SELECT count(*) FROM flights");
        BigDecimal revenue = jdbc.queryForObject(
                "SELECT coalesce(sum(price), 0) FROM bookings WHERE status IN ('PAID','CHECKED_IN','COMPLETED')",
                BigDecimal.class);
        List<AnalyticsResponse.GroupCount> byStatus = jdbc.query(
                "SELECT status, count(*) amount FROM bookings GROUP BY status ORDER BY status",
                (rs, row) -> new AnalyticsResponse.GroupCount(rs.getString("status"), rs.getLong("amount")));
        List<AnalyticsResponse.GroupCount> byFare = jdbc.query(
                "SELECT fare_class, count(*) amount FROM bookings GROUP BY fare_class ORDER BY fare_class",
                (rs, row) -> new AnalyticsResponse.GroupCount(rs.getString("fare_class"), rs.getLong("amount")));
        return new AnalyticsResponse(total, active, archived, passengers, flights,
                revenue == null ? BigDecimal.ZERO : revenue, byStatus, byFare);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
