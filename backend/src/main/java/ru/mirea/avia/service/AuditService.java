package ru.mirea.avia.service;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.mirea.avia.dto.AuditEventResponse;
import ru.mirea.avia.security.AuthUser;
import ru.mirea.avia.security.CurrentUser;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(String eventType, String entityType, UUID entityId, String description) {
        AuthUser user = CurrentUser.find().orElse(null);
        jdbc.update("""
                INSERT INTO audit_events (user_id, username, event_type, entity_type, entity_id, description, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, user == null ? null : user.id(), user == null ? null : user.login(), eventType, entityType,
                entityId, description, MDC.get("correlationId"));
    }

    public List<AuditEventResponse> find(String query, String eventType, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        String type = eventType == null ? "" : eventType.trim();
        return jdbc.query("""
                SELECT id, user_id, username, event_type, entity_type, entity_id, description, correlation_id, created_at
                FROM audit_events
                WHERE (? = '' OR lower(coalesce(username, '')) LIKE '%' || ? || '%'
                       OR lower(description) LIKE '%' || ? || '%'
                       OR lower(coalesce(correlation_id, '')) LIKE '%' || ? || '%')
                  AND (? = '' OR event_type = ?)
                ORDER BY created_at DESC LIMIT ?
                """, (rs, row) -> new AuditEventResponse(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("username"),
                rs.getString("event_type"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                rs.getString("description"), rs.getString("correlation_id"),
                rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)),
                q, q, q, q, type, type, Math.max(1, Math.min(limit, 500)));
    }
}
