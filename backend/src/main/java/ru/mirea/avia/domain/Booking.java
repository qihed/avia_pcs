package ru.mirea.avia.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "passenger_id")
  public Passenger passenger;

  @ManyToOne(optional = false)
  @JoinColumn(name = "flight_id")
  public Flight flight;

  @Column(nullable = false, length = 6, unique = true)
  public String bookingCode;

  @Column(nullable = false, length = 4)
  public String seatNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public FareClass fareClass;

  @Column(nullable = false, precision = 10, scale = 2)
  public BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  public BookingStatus status = BookingStatus.CREATED;

  @Column(nullable = false)
  public boolean extraBaggage;

  @Column(nullable = false)
  public boolean archived;

  @Version
  public long version;

  @Column(nullable = false)
  public LocalDateTime createdAt;

  @Column(nullable = false)
  public LocalDateTime updatedAt;

  /** Теги брони (таблица booking_tags из V2). Хранятся нормализованными: см. BookingTags. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "booking_tags",
    joinColumns = @JoinColumn(name = "booking_id")
  )
  @Column(name = "tag", nullable = false, length = 40)
  @org.hibernate.annotations.SortNatural
  @org.hibernate.annotations.BatchSize(size = 100)
  public SortedSet<String> tags = new TreeSet<>();
}
