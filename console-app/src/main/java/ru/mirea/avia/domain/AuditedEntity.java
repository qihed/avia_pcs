package ru.mirea.avia.domain;

import java.time.LocalDateTime;

/**
 * Общие технические поля изменяемых сущностей.
 *
 * <p>JPA в проекте нет, поэтому метки времени выставляет репозиторий через
 * {@link #onCreate(LocalDateTime)} и {@link #onUpdate(LocalDateTime)}, а при чтении из БД
 * они восстанавливаются методом {@link #restoreAudit(LocalDateTime, LocalDateTime)}.</p>
 */
public abstract class AuditedEntity {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Заполняет временные метки перед первой вставкой. */
    public void onCreate(LocalDateTime now) {
        createdAt = now;
        updatedAt = now;
    }

    /** Обновляет техническую метку перед изменением. */
    public void onUpdate(LocalDateTime now) {
        updatedAt = now;
    }

    /** Восстанавливает метки времени записи, прочитанной из БД. */
    public void restoreAudit(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
