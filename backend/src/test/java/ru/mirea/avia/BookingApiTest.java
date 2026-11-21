package ru.mirea.avia;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.*;
import ru.mirea.avia.repository.*;
import ru.mirea.avia.security.AuthUser;

@SpringBootTest(properties = "app.permanent-delete-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingApiTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper mapper;

  @Autowired
  PassengerRepository passengers;

  @Autowired
  FlightRepository flights;

  @Autowired
  BookingRepository bookings;

  Passenger passenger;
  Flight flight;
  final UUID author = UUID.randomUUID();

  @BeforeEach
  void fixture() {
    passenger = new Passenger();
    passenger.lastName = "Тестов";
    passenger.firstName = "Иван";
    passenger.birthDate = LocalDate.of(1990, 1, 1);
    passenger.documentType = DocumentType.PASSPORT_RF;
    passenger.documentNumber = "1234567890";
    passenger.email = UUID.randomUUID() + "@test.ru";
    passenger.phone = "+79991234567";
    passenger.createdAt = LocalDateTime.now();
    passengers.saveAndFlush(passenger);
    flight = new Flight();
    flight.flightNumber = "SU100";
    flight.airline = "Test";
    flight.departureAirport = "SVO";
    flight.arrivalAirport = "LED";
    flight.departureTime = LocalDateTime.now(ZoneOffset.UTC).plusDays(3);
    flight.arrivalTime = flight.departureTime.plusHours(2);
    flight.aircraftType = "A320";
    flight.totalSeats = 180;
    flight.basePrice = new BigDecimal("5000.00");
    flight.status = FlightStatus.SCHEDULED;
    flight.updatedAt = LocalDateTime.now();
    flights.saveAndFlush(flight);
  }

  RequestPostProcessor role(Role role) {
    return role(role, author);
  }

  RequestPostProcessor role(Role role, UUID id) {
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
    if (role != Role.VIEWER) authorities.add(
      new SimpleGrantedAuthority("ROLE_EDITOR")
    );
    if (role == Role.ADMIN) authorities.add(
      new SimpleGrantedAuthority("ROLE_ADMIN")
    );
    return authentication(
      new UsernamePasswordAuthenticationToken(
        new AuthUser(id, "test", "Тест", false, Set.of(role)),
        null,
        authorities
      )
    );
  }

  String body(String seat, Long version) throws Exception {
    Map<String, Object> m = new HashMap<>();
    m.put("passengerId", passenger.id);
    m.put("flightId", flight.id);
    m.put("seatNumber", seat);
    m.put("fareClass", "ECONOMY");
    m.put("price", 5000);
    m.put("extraBaggage", true);
    if (version != null) m.put("version", version);
    return mapper.writeValueAsString(m);
  }

  JsonNode create() throws Exception {
    return mapper.readTree(
      mvc
        .perform(
          post("/api/items")
            .with(role(Role.EDITOR))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("12A", null))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bookingCode").isString())
        .andReturn()
        .getResponse()
        .getContentAsString()
    );
  }

  @Test
  void crudVersionsArchiveRestoreAndPermanent() throws Exception {
    JsonNode b = create();
    String id = b.get("id").asText();
    mvc
      .perform(get("/api/items/" + id).with(role(Role.VIEWER)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.extraBaggage").value(true));
    mvc
      .perform(
        put("/api/items/" + id)
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12B", 0L))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(1));
    mvc
      .perform(
        put("/api/items/" + id)
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12C", 0L))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    mvc
      .perform(
        delete("/api/items/" + id + "?version=1").with(role(Role.EDITOR))
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        patch("/api/items/" + id + "/status")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"CANCELLED\",\"version\":1}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        delete("/api/items/" + id + "?version=2").with(role(Role.EDITOR))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.archived").value(true));
    mvc
      .perform(get("/api/items?archived=true").with(role(Role.VIEWER)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(1));
    mvc
      .perform(
        post("/api/items/" + id + "/restore")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"version\":3}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.archived").value(false));
    mvc
      .perform(
        delete("/api/items/" + id + "?version=4").with(role(Role.EDITOR))
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        delete("/api/items/" + id + "/permanent?version=5").with(
          role(Role.EDITOR)
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        delete("/api/items/" + id + "/permanent?version=5").with(
          role(Role.ADMIN)
        )
      )
      .andExpect(status().isNoContent());
    mvc
      .perform(get("/api/items/" + id).with(role(Role.VIEWER)))
      .andExpect(status().isNotFound());
  }

  @Test
  void seatDuplicateAndValidation() throws Exception {
    create();
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12A", null))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SEAT_TAKEN"));
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12B", null))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_BOOKING"));
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("0A", null))
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("99A", null))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void permissionsAndReferenceProtection() throws Exception {
    mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.VIEWER))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12A", null))
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(get("/api/references/flights").with(role(Role.VIEWER)))
      .andExpect(status().isOk());
    mvc
      .perform(
        delete("/api/references/flights/" + flight.id + "?version=0").with(
          role(Role.EDITOR)
        )
      )
      .andExpect(status().isForbidden());
    create();
    mvc
      .perform(
        delete("/api/references/flights/" + flight.id + "?version=0").with(
          role(Role.ADMIN)
        )
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        delete("/api/references/passengers/" + passenger.id).with(
          role(Role.ADMIN)
        )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void notesAuthorScopeAndPinning() throws Exception {
    String id = create().get("id").asText();
    String endpoint = "/api/items/" + id + "/notes";
    String note = mapper
      .readTree(
        mvc
          .perform(
            post(endpoint)
              .with(role(Role.EDITOR))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"text\":\"Позвонить пассажиру\",\"pinned\":true}")
          )
          .andExpect(status().isCreated())
          .andReturn()
          .getResponse()
          .getContentAsString()
      )
      .get("id")
      .asText();
    mvc
      .perform(get(endpoint).with(role(Role.VIEWER)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].pinned").value(true))
      .andExpect(jsonPath("$[0].authorName").value("Тест"));
    mvc
      .perform(
        put(endpoint + "/" + note)
          .with(role(Role.EDITOR, UUID.randomUUID()))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"text\":\"Чужое изменение\"}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        put(endpoint + "/" + note)
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"text\":\"Готово\",\"pinned\":false}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.text").value("Готово"));
    mvc
      .perform(
        post(endpoint)
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"text\":\"   \"}")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        delete(endpoint + "/" + note).with(role(Role.ADMIN, UUID.randomUUID()))
      )
      .andExpect(status().isNoContent());
  }

  @Test
  void expiredBookingReleasesSeatAndInvalidatesOldVersion() throws Exception {
    String id = create().get("id").asText();
    Booking b = bookings.findById(UUID.fromString(id)).orElseThrow();
    b.createdAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(31);
    bookings.saveAndFlush(b);
    mvc
      .perform(get("/api/items/" + id).with(role(Role.VIEWER)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("EXPIRED"))
      .andExpect(jsonPath("$.urgencyLevel").value("NONE"));
    create();
    assertThat(bookings.count()).isEqualTo(2);
  }

  @Test
  void invalidTransitionAndInactiveFlight() throws Exception {
    String id = create().get("id").asText();
    mvc
      .perform(
        patch("/api/items/" + id + "/status")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"COMPLETED\",\"version\":0}")
      )
      .andExpect(status().isConflict());
    Flight f = flights.findById(flight.id).orElseThrow();
    f.active = false;
    flights.saveAndFlush(f);
    mvc
      .perform(
        post("/api/items")
          .with(role(Role.EDITOR))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body("12B", null))
      )
      .andExpect(status().isConflict());
  }
}
