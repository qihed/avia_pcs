package ru.mirea.avia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Теги брони (PUT /api/items/{id}/tags, GET /api/items/tags) и массовые операции POST /api/items/bulk. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingTagsBulkApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PassengerRepository passengers;
    @Autowired FlightRepository flights;
    @Autowired BookingRepository bookings;

    BookingFixtures data;

    @BeforeEach
    void fixture() {
        bookings.deleteAll();
        data = new BookingFixtures(passengers, flights, bookings);
    }

    ResultActions putTags(UUID id, List<String> tags, long version, Role role) throws Exception {
        return mvc.perform(put("/api/items/" + id + "/tags").with(BookingFixtures.as(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tags", tags, "version", version))));
    }

    ResultActions bulk(Map<String, Object> body, Role role) throws Exception {
        return mvc.perform(post("/api/items/bulk").with(BookingFixtures.as(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    Map<String, Object> bulkBody(String action, Object... ids) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("ids", List.of(ids));
        return body;
    }

    @Test
    void tagsAreNormalizedDeduplicatedAndSorted() throws Exception {
        putTags(data.b3.id, List.of(" VIP ", "vip", "Позвонить   клиенту", "a-1_b.c"), data.b3.version, Role.EDITOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", contains("a-1_b.c", "vip", "позвонить клиенту")))
                .andExpect(jsonPath("$.version").value(data.b3.version + 1));

        // пустой список снимает все теги
        putTags(data.b3.id, List.of(), data.b3.version + 1, Role.EDITOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", empty()));
    }

    @Test
    void tagsAreValidated() throws Exception {
        putTags(data.b3.id, List.of("ok", "<script>", " "), data.b3.version, Role.EDITOR)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TAGS"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add("t" + i);
        putTags(data.b3.id, eleven, data.b3.version, Role.EDITOR).andExpect(status().isBadRequest());

        putTags(data.b3.id, List.of("x".repeat(41)), data.b3.version, Role.EDITOR).andExpect(status().isBadRequest());
    }

    @Test
    void tagsRespectVersionArchiveAndRoles() throws Exception {
        putTags(data.b3.id, List.of("vip"), data.b3.version + 7, Role.EDITOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        putTags(data.b5.id, List.of("vip"), data.b5.version, Role.EDITOR)
                .andExpect(status().isConflict());
        putTags(data.b3.id, List.of("vip"), data.b3.version, Role.VIEWER)
                .andExpect(status().isForbidden());
    }

    @Test
    void tagUsageCountsCurrentBookings() throws Exception {
        mvc.perform(get("/api/items/tags").with(BookingFixtures.as(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tag").value("vip"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].tag").value("корпоратив"))
                .andExpect(jsonPath("$[1].count").value(1));
    }

    @Test
    void bulkAddAndRemoveTag() throws Exception {
        Map<String, Object> add = bulkBody("ADD_TAG", data.b1.id, data.b3.id, UUID.randomUUID());
        add.put("tag", " Срочно ");
        bulk(add, Role.EDITOR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failures[0].message").value("Бронь не найдена"));
        assertThat(bookings.findById(data.b3.id).orElseThrow().tags).containsExactly("срочно");

        Map<String, Object> remove = bulkBody("REMOVE_TAG", data.b1.id, data.b2.id, data.b4.id);
        remove.put("tag", "vip");
        // у b4 тега нет — это не ошибка, бронь просто не меняется
        bulk(remove, Role.EDITOR).andExpect(jsonPath("$.succeeded").value(3));
        assertThat(bookings.findById(data.b1.id).orElseThrow().tags).containsExactly("срочно");
        assertThat(bookings.findById(data.b2.id).orElseThrow().tags).containsExactly("корпоратив");
    }

    @Test
    void bulkStatusAppliesBusinessRulesPerBooking() throws Exception {
        Map<String, Object> cancel = bulkBody("SET_STATUS", data.b2.id, data.b4.id, data.b2.id);
        cancel.put("status", "CANCELLED");
        bulk(cancel, Role.EDITOR)
                .andExpect(status().isOk())
                // повторный id считается один раз
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failures[0].bookingCode").value("PET004"))
                .andExpect(jsonPath("$.failures[0].message").value("Недопустимый переход статуса"));
        // удачная часть сохранена, хотя по другой брони был отказ
        assertThat(bookings.findById(data.b2.id).orElseThrow().status).isEqualTo(BookingStatus.CANCELLED);

        // регистрация PAID → CHECKED_IN проходит для обеих оплаченных броней
        Map<String, Object> checkIn = bulkBody("SET_STATUS", data.b1.id, data.b3.id);
        checkIn.put("status", "CHECKED_IN");
        bulk(checkIn, Role.EDITOR).andExpect(jsonPath("$.succeeded").value(2));
    }

    @Test
    void bulkArchiveAndRestore() throws Exception {
        bulk(bulkBody("ARCHIVE", data.b4.id, data.b1.id), Role.EDITOR)
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failures[0].bookingCode").value("IVA001"));
        assertThat(bookings.findById(data.b4.id).orElseThrow().archived).isTrue();

        bulk(bulkBody("RESTORE", data.b4.id, data.b5.id), Role.EDITOR)
                .andExpect(jsonPath("$.succeeded").value(2));
        assertThat(bookings.findById(data.b5.id).orElseThrow().archived).isFalse();

        // теги архивной брони не меняются и массово
        Map<String, Object> tagArchived = bulkBody("ADD_TAG", data.b4.id);
        tagArchived.put("tag", "x");
        bulk(bulkBody("ARCHIVE", data.b4.id), Role.EDITOR).andExpect(jsonPath("$.succeeded").value(1));
        bulk(tagArchived, Role.EDITOR).andExpect(jsonPath("$.succeeded").value(0));
    }

    @Test
    void bulkRequestIsValidated() throws Exception {
        bulk(bulkBody("ARCHIVE"), Role.EDITOR)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        bulk(bulkBody("SET_STATUS", data.b1.id), Role.EDITOR).andExpect(status().isBadRequest());
        bulk(bulkBody("ADD_TAG", data.b1.id), Role.EDITOR)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TAGS"));
        bulk(Map.of("action", "EXPLODE", "ids", List.of(data.b1.id)), Role.EDITOR)
                .andExpect(status().isBadRequest());

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) tooMany.add(UUID.randomUUID());
        bulk(Map.of("action", "ARCHIVE", "ids", tooMany), Role.EDITOR).andExpect(status().isBadRequest());

        bulk(bulkBody("ARCHIVE", data.b4.id), Role.VIEWER).andExpect(status().isForbidden());
    }
}
