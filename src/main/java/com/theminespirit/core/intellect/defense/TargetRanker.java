package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TargetRanker {
    private static final Point ORIGIN = new Point(0, 0, 0);

    private TargetRanker() {
    }

    public static List<RankedTarget> rank(DefenderRole role, boolean archerPresent, List<EnemyFact> enemies,
            DefenseProfile profile) {
        if (role == null) {
            throw new IllegalArgumentException(
                    "role не может быть null: порядок целей разный у лучника и мечника");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (profile == null) {
            throw new IllegalArgumentException(
                    "profile не может быть null: ранг цели считается по таблице приоритетов");
        }
        List<TargetClass> order = profile.orderFor(role, archerPresent);
        List<RankedTarget> ranked = new ArrayList<>(enemies.size());
        for (EnemyFact enemy : enemies) {
            TargetClass cls = profile.classify(enemy.type());
            int index = order.indexOf(cls);
            ranked.add(new RankedTarget(enemy, cls, index < 0 ? order.size() : index,
                    enemy.pos().distanceTo(ORIGIN)));
        }
        ranked.sort(Comparator.comparingInt(RankedTarget::rank).thenComparingDouble(RankedTarget::distance)
                .thenComparing(target -> target.enemy().id()));
        return List.copyOf(ranked);
    }

    public static Optional<RankedTarget> best(DefenderRole role, boolean archerPresent, List<EnemyFact> enemies,
            DefenseProfile profile) {
        return rank(role, archerPresent, enemies, profile).stream().findFirst();
    }
}
