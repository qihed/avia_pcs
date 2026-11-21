package ru.mirea.avia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.Role;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ModeratorPermissionsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"flights", "passengers"})
    void moderatorCanCreateAndEditButOnlyAdminCanDelete(String kind) throws Exception {
        LocalDateTime departure = LocalDateTime.now(ZoneOffset.UTC).plusDays(10);
        Map<String, Object> body = kind.equals("flights") ? new java.util.HashMap<>(Map.of(
                "flightNumber", "XY123", "airline", "Тест", "departureAirport", "SVO", "arrivalAirport", "LED",
                "departureTime", departure, "arrivalTime", departure.plusHours(2), "aircraftType", "A320",
                "totalSeats", 180, "basePrice", 5000, "status", "SCHEDULED")) : new java.util.HashMap<>(Map.of(
                "lastName", "Тестов", "firstName", "Иван", "birthDate", "1990-01-01", "documentType", "PASSPORT_RF",
                "documentNumber", "4510123456", "email", UUID.randomUUID() + "@example.org", "phone", "+79990000000"));
        if (kind.equals("flights")) body.put("active", true);
        String collection = "/api/references/" + kind;
        var created = mvc.perform(post(collection).with(BookingFixtures.as(Role.MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        var json = mapper.readTree(created.getResponse().getContentAsString());
        String path = collection + "/" + json.get("id").asText();
        body.put(kind.equals("flights") ? "airline" : "lastName", "Изменено");
        if (kind.equals("flights")) body.put("version", json.get("version").asLong());
        var updated = mvc.perform(put(path).with(BookingFixtures.as(Role.MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        String deletion = path + (kind.equals("flights") ? "?version=" +
                mapper.readTree(updated.getResponse().getContentAsString()).get("version").asLong() : "");
        mvc.perform(delete(deletion).with(BookingFixtures.as(Role.MODERATOR)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(path).with(BookingFixtures.as(Role.MODERATOR))).andExpect(status().isOk());
        mvc.perform(delete(deletion).with(BookingFixtures.as(Role.ADMIN))).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "EDITOR"})
    void legacyRolesCannotBeAssigned(String role) throws Exception {
        mvc.perform(post("/api/admin/users").with(BookingFixtures.as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "login", "legacy-user", "displayName", "Имя", "roles", java.util.List.of(role)))))
                .andExpect(status().isBadRequest());
    }
}
