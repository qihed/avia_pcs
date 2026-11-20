package ru.mirea.avia.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import ru.mirea.avia.domain.*;

public record BookingResponse(
  UUID id,
  String bookingCode,
  Passenger passenger,
  Flight flight,
  String seatNumber,
  FareClass fareClass,
  BigDecimal price,
  BookingStatus status,
  boolean extraBaggage,
  boolean archived,
  long version,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  UrgencyLevel urgencyLevel,
  java.util.List<String> tags
) {
  public static BookingResponse of(Booking b, Clock clock) {
    return new BookingResponse(
      b.id,
      b.bookingCode,
      b.passenger,
      b.flight,
      b.seatNumber,
      b.fareClass,
      b.price,
      b.status,
      b.extraBaggage,
      b.archived,
      b.version,
      b.createdAt,
      b.updatedAt,
      UrgencyLevel.calculate(
        b.status,
        b.archived,
        b.flight.departureTime,
        clock
      ),
      java.util.List.copyOf(b.tags)
    );
  }
}
