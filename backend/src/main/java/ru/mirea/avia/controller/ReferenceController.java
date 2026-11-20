package ru.mirea.avia.controller;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.dto.*;
import ru.mirea.avia.service.ReferenceService;

@RestController
@RequestMapping("/api/references")
public class ReferenceController {

  private final ReferenceService service;

  public ReferenceController(ReferenceService s) {
    service = s;
  }

  @GetMapping("/passengers")
  public List<Passenger> passengers() {
    return service.passengers();
  }

  @GetMapping("/passengers/{id}")
  public Passenger passenger(@PathVariable UUID id) {
    return service.passenger(id);
  }

  @PostMapping("/passengers")
  @ResponseStatus(HttpStatus.CREATED)
  public Passenger createPassenger(@Valid @RequestBody PassengerRequest r) {
    return service.savePassenger(null, r);
  }

  @PutMapping("/passengers/{id}")
  public Passenger updatePassenger(
    @PathVariable UUID id,
    @Valid @RequestBody PassengerRequest r
  ) {
    return service.savePassenger(id, r);
  }

  @DeleteMapping("/passengers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePassenger(@PathVariable UUID id) {
    service.deletePassenger(id);
  }

  @GetMapping("/flights")
  public List<Flight> flights() {
    return service.flights();
  }

  @GetMapping("/flights/{id}")
  public Flight flight(@PathVariable UUID id) {
    return service.flight(id);
  }

  @PostMapping("/flights")
  @ResponseStatus(HttpStatus.CREATED)
  public Flight createFlight(@Valid @RequestBody FlightRequest r) {
    return service.saveFlight(null, r);
  }

  @PutMapping("/flights/{id}")
  public Flight updateFlight(
    @PathVariable UUID id,
    @Valid @RequestBody FlightRequest r
  ) {
    return service.saveFlight(id, r);
  }

  @DeleteMapping("/flights/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFlight(@PathVariable UUID id, @RequestParam long version) {
    service.deleteFlight(id, version);
  }
}
