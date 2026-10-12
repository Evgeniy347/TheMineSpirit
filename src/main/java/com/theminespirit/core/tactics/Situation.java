package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Situation(SelfFacts self, List<EnemyFact> enemies, EnvFacts env, Orders orders, int ticksSinceHit,
        int ticksSinceStrike) {
    public static final int MAX_ENEMIES = 16;
    public static final int MAX_TICKS = 1_000_000;

    public Situation {
        FactChecks.required("Situation.self", self);
        FactChecks.required("Situation.enemies", enemies);
        FactChecks.required("Situation.env", env);
        FactChecks.required("Situation.orders", orders);
        FactChecks.range("Situation.ticksSinceHit", ticksSinceHit, 0, MAX_TICKS);
        FactChecks.range("Situation.ticksSinceStrike", ticksSinceStrike, 0, MAX_TICKS);
        enemies = canonical(self.pos(), enemies);
    }

    public double distance(EnemyFact enemy) {
        return enemy.pos().distanceTo(self.pos());
    }

    private static List<EnemyFact> canonical(Point origin, List<EnemyFact> source) {
        Comparator<EnemyFact> order = comparator(origin);
        List<EnemyFact> sorted = new ArrayList<>(source);
        sorted.sort(order);
        if (sorted.size() <= MAX_ENEMIES) {
            return List.copyOf(sorted);
        }
        List<EnemyFact> result = new ArrayList<>(sorted.subList(0, MAX_ENEMIES));
        List<EnemyFact> folded = foldByType(sorted.subList(MAX_ENEMIES, sorted.size()));
        folded.sort(order);
        result.addAll(folded);
        return List.copyOf(result);
    }

    private static Comparator<EnemyFact> comparator(Point origin) {
        return Comparator.<EnemyFact>comparingDouble(enemy -> enemy.pos().distanceTo(origin))
                .thenComparing(EnemyFact::type)
                .thenComparingDouble(EnemyFact::hpFraction)
                .thenComparing(EnemyFact::id)
                .thenComparingDouble(enemy -> enemy.pos().x())
                .thenComparingDouble(enemy -> enemy.pos().y())
                .thenComparingDouble(enemy -> enemy.pos().z())
                .thenComparingDouble(EnemyFact::armor)
                .thenComparing(EnemyFact::visible)
                .thenComparing(EnemyFact::targetingSelf)
                .thenComparingInt(EnemyFact::count);
    }

    private static List<EnemyFact> foldByType(List<EnemyFact> tail) {
        Map<String, List<EnemyFact>> byType = new TreeMap<>();
        for (EnemyFact enemy : tail) {
            byType.computeIfAbsent(enemy.type(), type -> new ArrayList<>()).add(enemy);
        }
        List<EnemyFact> folded = new ArrayList<>();
        for (List<EnemyFact> group : byType.values()) {
            folded.add(merge(group));
        }
        return folded;
    }

    private static EnemyFact merge(List<EnemyFact> group) {
        EnemyFact first = group.get(0);
        int count = 0;
        double hp = 0;
        double armor = 0;
        boolean visible = false;
        boolean targeting = false;
        for (EnemyFact enemy : group) {
            count += enemy.count();
            hp += enemy.hpFraction() * enemy.count();
            armor = Math.max(armor, enemy.armor());
            visible |= enemy.visible();
            targeting |= enemy.targetingSelf();
        }
        return new EnemyFact(first.type(), first.id(), first.pos(), Math.min(1, hp / count),
                armor, visible, targeting, Math.min(EnemyFact.MAX_COUNT, count));
    }
}
