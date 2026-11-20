package ru.mirea.avia.service;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.dto.NoteRequest;
import ru.mirea.avia.error.*;
import ru.mirea.avia.repository.*;
import ru.mirea.avia.security.*;

@Service
@Transactional
public class BookingNoteService {

  private final BookingRepository bookings;
  private final BookingNoteRepository notes;
  private final Clock clock;

  public BookingNoteService(
    BookingRepository b,
    BookingNoteRepository n,
    Clock c
  ) {
    bookings = b;
    notes = n;
    clock = c;
  }

  private void parent(UUID id, boolean write) {
    Booking b = (
      write ? bookings.lockById(id) : bookings.findById(id)
    ).orElseThrow(() -> new NotFoundException("Бронь не найдена"));
    if (write && b.archived) throw new ConflictException(
      "Заметки архивной брони доступны только для чтения"
    );
  }

  public List<BookingNote> list(UUID id) {
    parent(id, false);
    return notes.findByBookingIdOrderByPinnedDescCreatedAtDesc(id);
  }

  public BookingNote create(UUID id, NoteRequest r) {
    parent(id, true);
    AuthUser u = CurrentUser.require();
    BookingNote n = new BookingNote();
    n.bookingId = id;
    n.authorId = u.id();
    n.authorName = u.displayName();
    n.createdAt = clock.instant();
    return save(n, r);
  }

  private BookingNote own(UUID booking, UUID id) {
    BookingNote n = notes
      .findByIdAndBookingId(id, booking)
      .orElseThrow(() -> new NotFoundException("Заметка не найдена"));
    AuthUser u = CurrentUser.require();
    if (
      !u.id().equals(n.authorId) && !u.hasRole(Role.ADMIN)
    ) throw new ForbiddenException(
      "Изменять заметку может автор или администратор"
    );
    return n;
  }

  private BookingNote save(BookingNote n, NoteRequest r) {
    n.text = r.text().trim();
    n.pinned = r.pinned();
    n.updatedAt = clock.instant();
    return notes.saveAndFlush(n);
  }

  public BookingNote update(UUID booking, UUID id, NoteRequest r) {
    parent(booking, true);
    return save(own(booking, id), r);
  }

  public void delete(UUID booking, UUID id) {
    parent(booking, true);
    notes.delete(own(booking, id));
    notes.flush();
  }
}
