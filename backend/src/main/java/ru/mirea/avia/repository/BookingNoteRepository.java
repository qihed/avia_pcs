package ru.mirea.avia.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.avia.domain.BookingNote;

public interface BookingNoteRepository
  extends JpaRepository<BookingNote, UUID>
{
  List<BookingNote> findByBookingIdOrderByPinnedDescCreatedAtDesc(
    UUID bookingId
  );
  Optional<BookingNote> findByIdAndBookingId(UUID id, UUID bookingId);
  void deleteByBookingId(UUID bookingId);
}
