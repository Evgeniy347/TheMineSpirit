package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SquadOrder(
        String squad,
        SquadDecision decision,
        Point post,
        double bearing,
        Optional<String> reinforce,
        CombatStance stance,
        Map<UUID, CombatStance> memberStances,
        Map<UUID, DefensePotion> memberPotions,
        List<Double> sectors,
        int members,
        int targets,
        String reason) {
    public SquadOrder {
        Objects.requireNonNull(squad, "squad не может быть null: решение адресуется отряду");
        Objects.requireNonNull(decision, "decision не может быть null: отряд без решения не исполнит приказ");
        Objects.requireNonNull(post, "post не может быть null: без точки отряд не знает, куда идти");
        Objects.requireNonNull(reinforce, "reinforce не может быть null: нет подмоги — Optional.empty()");
        Objects.requireNonNull(stance, "stance не может быть null: решение отряда включает стойку");
        if (!Double.isFinite(bearing)) {
            throw new IllegalArgumentException("bearing = " + bearing + ": направление обязано быть конечным числом");
        }
        memberStances = memberStances == null ? Map.of() : Map.copyOf(memberStances);
        memberPotions = memberPotions == null ? Map.of() : Map.copyOf(memberPotions);
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
        if (members <= 0) {
            throw new IllegalArgumentException("members = " + members + ": отряд без духов не решает");
        }
        if (targets < 0) {
            throw new IllegalArgumentException("targets = " + targets + ": число целей не может быть отрицательным");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason не может быть пустым: решение обязано объяснять ход");
        }
    }
}
