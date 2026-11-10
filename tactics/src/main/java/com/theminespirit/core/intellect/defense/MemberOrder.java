package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MemberOrder(
        UUID member,
        String squad,
        SquadDecision decision,
        Point post,
        CombatStance stance,
        Optional<EnemyFact> target,
        Optional<Double> holdDistance,
        Optional<DefensePotion> potion,
        String reason) {
    public MemberOrder {
        Objects.requireNonNull(member, "member не может быть null: приказ адресуется конкретному духу");
        Objects.requireNonNull(squad, "squad не может быть null: приказ принадлежит отряду");
        Objects.requireNonNull(decision, "decision не может быть null: без решения приказ не исполнить");
        Objects.requireNonNull(post, "post не может быть null: без точки дух не знает, куда встать");
        Objects.requireNonNull(stance, "stance не может быть null: бой без стойки не исполнить");
        Objects.requireNonNull(target, "target не может быть null: нет цели — Optional.empty()");
        Objects.requireNonNull(holdDistance, "holdDistance не может быть null: нет дистанции — Optional.empty()");
        Objects.requireNonNull(potion, "potion не может быть null: не пьёт — Optional.empty()");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason не может быть пустым: приказ обязан объяснять решение");
        }
    }
}
