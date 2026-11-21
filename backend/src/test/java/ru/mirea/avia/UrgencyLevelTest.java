package ru.mirea.avia;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ru.mirea.avia.domain.*;

class UrgencyLevelTest {

  private static final Clock CLOCK = Clock.fixed(
    Instant.parse("2026-11-20T10:00:00Z"),
    ZoneOffset.UTC
  );
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(
    CLOCK.instant(),
    ZoneOffset.UTC
  );

  @ParameterizedTest
  @CsvSource({
    "-1,NONE",
    "0,NONE",
    "1,CRITICAL",
    "21599,CRITICAL",
    "21600,CRITICAL",
    "21601,HIGH",
    "86399,HIGH",
    "86400,HIGH",
    "86401,MEDIUM",
    "259199,MEDIUM",
    "259200,MEDIUM",
    "259201,LOW",
  })
  void exactBoundaries(long seconds, UrgencyLevel expected) {
    assertThat(
      UrgencyLevel.calculate(
        BookingStatus.CREATED,
        false,
        NOW.plusSeconds(seconds),
        CLOCK
      )
    ).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(
    value = BookingStatus.class,
    names = { "COMPLETED", "CANCELLED", "EXPIRED" }
  )
  void terminalBookingsHaveNoUrgency(BookingStatus status) {
    assertThat(
      UrgencyLevel.calculate(status, false, NOW.plusHours(1), CLOCK)
    ).isEqualTo(UrgencyLevel.NONE);
  }

  @ParameterizedTest
  @EnumSource(BookingStatus.class)
  void archivedBookingsHaveNoUrgency(BookingStatus status) {
    assertThat(
      UrgencyLevel.calculate(status, true, NOW.plusHours(1), CLOCK)
    ).isEqualTo(UrgencyLevel.NONE);
  }

  @ParameterizedTest
  @EnumSource(
    value = BookingStatus.class,
    names = { "CREATED", "PAID", "CHECKED_IN" }
  )
  void activeStatusesUseDeparture(BookingStatus status) {
    assertThat(
      UrgencyLevel.calculate(status, false, NOW.plusHours(12), CLOCK)
    ).isEqualTo(UrgencyLevel.HIGH);
  }

  @Test
  void independentOfMachineZone() {
    assertThat(
      UrgencyLevel.calculate(
        BookingStatus.PAID,
        false,
        NOW.plusHours(7),
        CLOCK.withZone(ZoneId.of("Asia/Tokyo"))
      )
    ).isEqualTo(UrgencyLevel.HIGH);
  }

  @Test
  void doesNotRoundSubsecondBoundary() {
    assertThat(
      UrgencyLevel.calculate(
        BookingStatus.PAID,
        false,
        NOW.plusHours(6).plusNanos(1),
        CLOCK
      )
    ).isEqualTo(UrgencyLevel.HIGH);
  }

  @Test
  void nullDepartureIsRejected() {
    assertThatNullPointerException().isThrownBy(() ->
      UrgencyLevel.calculate(BookingStatus.PAID, false, null, CLOCK)
    );
  }
}
