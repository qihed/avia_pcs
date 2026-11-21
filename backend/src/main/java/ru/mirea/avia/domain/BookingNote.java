package ru.mirea.avia.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_notes")
public class BookingNote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false)
  public UUID bookingId;

  public UUID authorId;

  @Column(nullable = false, length = 120)
  public String authorName;

  @Column(nullable = false, length = 2000)
  public String text;

  @Column(nullable = false)
  public boolean pinned;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;
}
