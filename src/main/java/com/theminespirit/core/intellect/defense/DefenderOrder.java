package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DefenderOrder(UUID defender, DefenderRole role, Optional<EnemyFact> target, CombatStance stance,
        Optional<Double> holdDistance, Optional<Point> cover, Optional<UUID> handOff, String reason) {
    public DefenderOrder {
        Objects.requireNonNull(defender, "defender не может быть null: приказ адресуется конкретному духу");
        Objects.requireNonNull(role, "role не может быть null: стойка зависит от специализации");
        Objects.requireNonNull(target, "target не может быть null: нет цели — Optional.empty()");
        Objects.requireNonNull(stance, "stance не может быть null: приказ без стойки не исполнить");
        Objects.requireNonNull(holdDistance, "holdDistance не может быть null: нет дистанции — Optional.empty()");
        Objects.requireNonNull(cover, "cover не может быть null: нет укрытия — Optional.empty()");
        Objects.requireNonNull(handOff, "handOff не может быть null: нет приёмника — Optional.empty()");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason не может быть пустым: trace приказа обязан объяснять стойку");
        }
    }
}
