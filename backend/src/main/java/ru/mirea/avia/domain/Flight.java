package ru.mirea.avia.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "flights")
public class Flight {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false, length = 8)
  public String flightNumber;

  @Column(nullable = false, length = 60)
  public String airline;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(nullable = false, columnDefinition = "char(3)")
  public String departureAirport;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(nullable = false, columnDefinition = "char(3)")
  public String arrivalAirport;

  @Column(nullable = false)
  public LocalDateTime departureTime;

  @Column(nullable = false)
  public LocalDateTime arrivalTime;

  @Column(nullable = false, length = 40)
  public String aircraftType;

  @Column(nullable = false)
  public int totalSeats;

  @Column(nullable = false, precision = 10, scale = 2)
  public BigDecimal basePrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public FlightStatus status;

  @Column(nullable = false)
  public boolean active = true;

  @Version
  public long version;

  @Column(nullable = false)
  public LocalDateTime updatedAt;
}
