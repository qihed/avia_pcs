package ru.mirea.avia.domain;

public enum BookingStatus {
  CREATED,
  PAID,
  CHECKED_IN,
  COMPLETED,
  CANCELLED,
  EXPIRED;

  public boolean occupiesSeat() {
    return this != CANCELLED && this != EXPIRED;
  }

  public boolean terminal() {
    return this == COMPLETED || this == CANCELLED || this == EXPIRED;
  }

  public boolean canChangeTo(BookingStatus next) {
    return switch (this) {
      case CREATED -> next == PAID || next == CANCELLED;
      case PAID -> next == CHECKED_IN || next == CANCELLED;
      case CHECKED_IN -> next == COMPLETED;
      default -> false;
    };
  }
}
