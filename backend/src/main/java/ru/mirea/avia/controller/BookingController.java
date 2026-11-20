package ru.mirea.avia.controller;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mirea.avia.domain.BookingNote;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.service.*;

@RestController
@RequestMapping("/api/items")
public class BookingController {

  private final BookingService bookings;
  private final BookingNoteService notes;

  public BookingController(BookingService b, BookingNoteService n) {
    bookings = b;
    notes = n;
  }

  @GetMapping
  public Page<BookingResponse> list(
    @RequestParam(defaultValue = "false") boolean archived,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return bookings.list(archived, page, size);
  }

  @GetMapping("/{id}")
  public BookingResponse get(@PathVariable UUID id) {
    return bookings.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse create(@Valid @RequestBody BookingRequest r) {
    return bookings.create(r);
  }

  @PutMapping("/{id}")
  public BookingResponse update(
    @PathVariable UUID id,
    @Valid @RequestBody BookingRequest r
  ) {
    return bookings.update(id, r);
  }

  @PatchMapping("/{id}/status")
  public BookingResponse status(
    @PathVariable UUID id,
    @Valid @RequestBody StatusRequest r
  ) {
    return bookings.status(id, r);
  }

  @DeleteMapping("/{id}")
  public BookingResponse archive(
    @PathVariable UUID id,
    @RequestParam long version
  ) {
    return bookings.archive(id, version, true);
  }

  @PostMapping("/{id}/restore")
  public BookingResponse restore(
    @PathVariable UUID id,
    @Valid @RequestBody VersionRequest r
  ) {
    return bookings.archive(id, r.version(), false);
  }

  @DeleteMapping("/{id}/permanent")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void permanent(@PathVariable UUID id, @RequestParam long version) {
    bookings.permanent(id, version);
  }

  @GetMapping("/{id}/notes")
  public List<BookingNote> notes(@PathVariable UUID id) {
    return notes.list(id);
  }

  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public BookingNote addNote(
    @PathVariable UUID id,
    @Valid @RequestBody NoteRequest r
  ) {
    return notes.create(id, r);
  }

  @PutMapping("/{id}/notes/{noteId}")
  public BookingNote updateNote(
    @PathVariable UUID id,
    @PathVariable UUID noteId,
    @Valid @RequestBody NoteRequest r
  ) {
    return notes.update(id, noteId, r);
  }

  @DeleteMapping("/{id}/notes/{noteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
    notes.delete(id, noteId);
  }
}
