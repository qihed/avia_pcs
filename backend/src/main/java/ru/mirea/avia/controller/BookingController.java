package ru.mirea.avia.controller;

import jakarta.validation.Valid;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.BookingNote;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.UrgencyLevel;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.dto.BookingBulkDtos.BulkRequest;
import ru.mirea.avia.dto.BookingBulkDtos.BulkResult;
import ru.mirea.avia.dto.BookingBulkDtos.TagUsage;
import ru.mirea.avia.dto.BookingBulkDtos.TagsRequest;
import ru.mirea.avia.filter.BookingFilter;
import ru.mirea.avia.filter.BookingSpecifications;
import ru.mirea.avia.filter.PageRequests;
import ru.mirea.avia.service.*;

@RestController
@RequestMapping("/api/items")
public class BookingController {

  private final BookingService bookings;
  private final BookingNoteService notes;
  private final AppProperties props;

  public BookingController(
    BookingService b,
    BookingNoteService n,
    AppProperties a
  ) {
    bookings = b;
    notes = n;
    props = a;
  }

  /**
   * Поиск, фильтры, сортировка, пагинация. Пример:
   * /api/items?q=иванов su&status=CREATED&status=PAID&fareClass=ECONOMY&tag=vip&urgency=HIGH
   * &priceMin=1000&priceMax=20000&departureFrom=2026-12-01&departureTo=2026-12-31&extraBaggage=true
   * &sort=price,desc&page=0&size=20
   */
  @GetMapping
  public PageResponse<BookingResponse> list(
    @RequestParam(required = false) String q,
    @RequestParam(defaultValue = "false") boolean archived,
    @RequestParam(name = "status", required = false) List<BookingStatus> statuses,
    @RequestParam(required = false) FareClass fareClass,
    @RequestParam(required = false) UUID flightId,
    @RequestParam(required = false) UUID passengerId,
    @RequestParam(name = "tag", required = false) List<String> tags,
    @RequestParam(required = false) UrgencyLevel urgency,
    @RequestParam(required = false) BigDecimal priceMin,
    @RequestParam(required = false) BigDecimal priceMax,
    @RequestParam(required = false) @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE
    ) LocalDate departureFrom,
    @RequestParam(required = false) @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE
    ) LocalDate departureTo,
    @RequestParam(required = false) Boolean extraBaggage,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(required = false) Integer size,
    @RequestParam(required = false) String sort
  ) {
    BookingFilter filter = new BookingFilter(
      q,
      archived,
      statuses,
      fareClass,
      flightId,
      passengerId,
      tags,
      urgency,
      priceMin,
      priceMax,
      departureFrom,
      departureTo,
      extraBaggage
    );
    return bookings.list(
      filter,
      PageRequests.of(
        page,
        size == null ? props.pagination().defaultPageSize() : size,
        sort,
        props.pagination().maxPageSize(),
        BookingSpecifications.SORT_FIELDS,
        BookingSpecifications.DEFAULT_SORT,
        "id"
      )
    );
  }

  /** Все теги текущих броней с количеством — для подсказок в форме и фильтре. */
  @GetMapping("/tags")
  public List<TagUsage> tags() {
    return bookings.tagUsage();
  }

  @PutMapping("/{id}/tags")
  public BookingResponse setTags(
    @PathVariable UUID id,
    @Valid @RequestBody TagsRequest r
  ) {
    return bookings.setTags(id, r.tags(), r.version());
  }

  /** Массовая операция: ADD_TAG, REMOVE_TAG, SET_STATUS, ARCHIVE, RESTORE. */
  @PostMapping("/bulk")
  public BulkResult bulk(@Valid @RequestBody BulkRequest r) {
    return bookings.bulk(r);
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
