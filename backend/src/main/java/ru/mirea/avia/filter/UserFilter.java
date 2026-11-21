package ru.mirea.avia.filter;

import ru.mirea.avia.domain.Role;

/**
 * Фильтр списка пользователей в админке.
 *
 * @param q       часть логина или имени (без учёта регистра)
 * @param role    у пользователя есть эта роль; null — любая
 * @param enabled true — только активные, false — только отключённые, null — все
 */
public record UserFilter(String q, Role role, Boolean enabled) {

    public static final int MAX_QUERY_LENGTH = 100;
}
