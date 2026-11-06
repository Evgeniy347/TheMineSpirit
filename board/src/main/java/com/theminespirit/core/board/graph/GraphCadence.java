package com.theminespirit.core.board.graph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class GraphCadence {
    public static final long INTERVAL_TICKS = 600;
    private static final Set<String> URGENT_EVENTS = Set.of(
            "hostile_nearby",
            "creeper_crater",
            "life_threat",
            "life_threat_spirit",
            "life_threat_player",
            "life_injury");

    private long lastCycle = -1;
    private Set<String> signals = Set.of();

    public boolean due(long now, Set<String> current) {
        Objects.requireNonNull(current, "Новый вход графа не может быть null: без него срочность не определить");
        if (changed(signals, current)) {
            return true;
        }
        if (lastCycle < 0 || now < lastCycle) {
            return true;
        }
        return now - lastCycle >= INTERVAL_TICKS;
    }

    public void finished(long now, Set<String> current) {
        Objects.requireNonNull(current, "Вход завершённого цикла не может быть null: по нему сравнивается следующий");
        lastCycle = now;
        signals = Set.copyOf(current);
    }

    public static boolean urgent(String event) {
        Objects.requireNonNull(event, "Событие не может быть null: срочность решается по имени события");
        return URGENT_EVENTS.contains(event);
    }

    public static Set<String> signals(Set<String> events, Collection<String> spirits, Collection<String> stuck) {
        Objects.requireNonNull(events, "События не могут быть null: без них срочность не определить");
        Objects.requireNonNull(spirits, "Духи не могут быть null: новый дух меняет вход графа");
        Objects.requireNonNull(stuck, "Застрявшие не могут быть null: застревание меняет вход графа");
        Set<String> signals = new HashSet<>();
        for (String event : events) {
            if (urgent(event)) {
                signals.add("event:" + event);
            }
        }
        for (String spirit : spirits) {
            signals.add("spirit:" + spirit);
        }
        for (String spirit : stuck) {
            signals.add("stuck:" + spirit);
        }
        return Set.copyOf(signals);
    }

    private static boolean changed(Set<String> previous, Set<String> current) {
        return !previous.equals(current);
    }
}
