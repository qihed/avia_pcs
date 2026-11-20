package ru.mirea.avia.domain;

import java.time.*;
import java.util.Objects;

/** Вычисляемое поле. Границы включены; время рейса трактуется как UTC, как и JDBC. */
public enum UrgencyLevel {
  NONE,
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  public static UrgencyLevel calculate(
    BookingStatus status,
    boolean archived,
    LocalDateTime departure,
    Clock clock
  ) {
    Objects.requireNonNull(status);
    Objects.requireNonNull(departure);
    Objects.requireNonNull(clock);
    if (archived || status.terminal()) return NONE;
    Duration remaining = Duration.between(
      LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
      departure
    );
    if (remaining.isNegative() || remaining.isZero()) return NONE;
    if (remaining.compareTo(Duration.ofHours(6)) <= 0) return CRITICAL;
    if (remaining.compareTo(Duration.ofHours(24)) <= 0) return HIGH;
    if (remaining.compareTo(Duration.ofHours(72)) <= 0) return MEDIUM;
    return LOW;
  }
}
