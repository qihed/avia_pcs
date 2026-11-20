package ru.mirea.avia.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsResponse(long totalBookings, long activeBookings, long archivedBookings,
                                long totalPassengers, long totalFlights, BigDecimal revenue,
                                List<GroupCount> bookingsByStatus, List<GroupCount> bookingsByFareClass) {
    public record GroupCount(String name, long count) {}
}
