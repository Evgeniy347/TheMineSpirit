package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InstinctDecision(
        CombatStance stance,
        Optional<Double> holdDistance,
        Optional<Point> cover,
        Optional<UUID> handOff,
        String reason) {
    public InstinctDecision {
        Objects.requireNonNull(stance, "stance не может быть null: стойка — суть инстинкта");
        Objects.requireNonNull(holdDistance, "holdDistance не может быть null: нет дистанции — Optional.empty()");
        Objects.requireNonNull(cover, "cover не может быть null: нет укрытия — Optional.empty()");
        Objects.requireNonNull(handOff, "handOff не может быть null: нет приёмника — Optional.empty()");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason не может быть пустым: trace решения обязан объяснять стойку");
        }
    }
}
