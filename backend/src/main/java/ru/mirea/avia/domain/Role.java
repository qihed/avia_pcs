package ru.mirea.avia.domain;

import java.util.EnumSet;
import java.util.Set;

/** Два уровня доступа: модерация и администрирование. Более высокая роль включает низшие. */
public enum Role {
    MODERATOR,
    ADMIN;

    /** Роль вместе со всеми, которые она включает: ADMIN -> ADMIN, MODERATOR. */
    public Set<Role> withIncluded() {
        EnumSet<Role> result = EnumSet.noneOf(Role.class);
        for (Role role : values()) {
            if (role.ordinal() <= ordinal()) {
                result.add(role);
            }
        }
        return result;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
