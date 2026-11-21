package ru.mirea.avia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingManagementRow(UUID id, String bookingCode, String passenger, String flightNumber,
                                   String status, BigDecimal price, boolean archived) {}
