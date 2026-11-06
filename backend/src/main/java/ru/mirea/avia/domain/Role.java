package ru.mirea.avia.domain;

import java.util.EnumSet;
import java.util.Set;

/** Три уровня доступа: просмотр, редактирование, администрирование. Более высокая роль включает низшие. */
public enum Role {
    VIEWER,
    EDITOR,
    ADMIN;

    /** Роль вместе со всеми, которые она включает: ADMIN -> ADMIN, EDITOR, VIEWER. */
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
