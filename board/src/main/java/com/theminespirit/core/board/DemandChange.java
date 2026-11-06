package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;

public final class DemandChange {
    private final StateChangeLog changes = new StateChangeLog();

    public boolean changed(String level, Optional<DemandRules.Need> need) {
        Objects.requireNonNull(level, "Уровень не может быть null: у каждого уровня своя доска и свои нужды");
        Objects.requireNonNull(need, "Нужда не может быть null: пустую задают Optional.empty()");
        String state = need.map(value -> value.kind() + ":" + value.subject() + ":" + value.level())
                .orElse("closed");
        return changes.changed(level, state);
    }
}
