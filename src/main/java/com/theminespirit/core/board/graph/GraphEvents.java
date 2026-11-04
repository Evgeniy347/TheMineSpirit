package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class GraphEvents {
    private GraphEvents() {}

    public static Set<String> merge(Set<String> known, Set<String> more) {
        Objects.requireNonNull(known, "Известные события не могут быть null: пустые задают пустым набором");
        Objects.requireNonNull(more, "Добавляемые события не могут быть null: пустые задают пустым набором");
        Set<String> events = new LinkedHashSet<>(known);
        events.addAll(more);
        return events;
    }
}
