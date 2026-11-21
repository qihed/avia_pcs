package ru.mirea.avia.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID userId, String username, String eventType, String entityType,
                                 UUID entityId, String description, String correlationId, OffsetDateTime createdAt) {}
