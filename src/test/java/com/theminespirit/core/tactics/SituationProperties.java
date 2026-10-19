package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SituationProperties {
    private static final Map<String, Double> DPS = Map.of(
            "minecraft:zombie", 3.0, "minecraft:skeleton", 4.0, "minecraft:creeper", 0.0, "minecraft:ravager", 8.0);

    @Provide
    Arbitrary<List<EnemyFact>> enemyLists() {
        Arbitrary<EnemyFact> enemy = Combinators.combine(
                        Arbitraries.of(DPS.keySet().toArray(String[]::new)),
                        Arbitraries.integers().between(0, 6),
                        Arbitraries.integers().between(0, 12),
                        Arbitraries.integers().between(0, 4),
                        Arbitraries.integers().between(1, 3),
                        Arbitraries.of(true, false))
                .as((type, id, distance, hpStep, count, visible) -> new EnemyFact(
                        type, "id" + id, new Point(distance, 0, 0), hpStep / 4.0, 0, visible, !visible, count));
        return enemy.list().ofMinSize(0).ofMaxSize(40);
    }

    private static Situation situation(List<EnemyFact> enemies) {
        SituationBuilder builder = SituationBuilder.create();
        enemies.forEach(builder::enemy);
        return builder.build();
    }

    @Property
    void situation_AnyEnemyPermutation_IsEqual(@ForAll("enemyLists") List<EnemyFact> enemies, @ForAll long seed) {
        List<EnemyFact> shuffled = new ArrayList<>(enemies);
        Collections.shuffle(shuffled, new Random(seed));

        assertEquals(
                situation(enemies),
                situation(shuffled),
                "Любая перестановка врагов даёт равную Situation: порядок, в котором адаптер нашёл сущностей, "
                        + "не должен менять решение. Если красно — сортировка Situation не полная (тай-брейк)");
    }

    @Property
    void situation_AnyEnemies_KeepsCountDamageAndHealth(@ForAll("enemyLists") List<EnemyFact> enemies) {
        Situation situation = situation(enemies);

        assertEquals(
                sumCount(enemies), sumCount(situation.enemies()), "Усечение сверх MAX_ENEMIES сохраняет число врагов");
        assertEquals(
                totalDamage(enemies),
                totalDamage(situation.enemies()),
                1e-9,
                "Усечение сохраняет суммарный урон: сворачивание хвоста не должно делать орду безопаснее");
        assertEquals(
                totalHealth(enemies),
                totalHealth(situation.enemies()),
                1e-9,
                "Усечение сохраняет суммарное HP врагов (доля × число)");
    }

    private static int sumCount(List<EnemyFact> enemies) {
        return enemies.stream().mapToInt(EnemyFact::count).sum();
    }

    private static double totalDamage(List<EnemyFact> enemies) {
        return enemies.stream()
                .mapToDouble(enemy -> enemy.count() * DPS.get(enemy.type()))
                .sum();
    }

    private static double totalHealth(List<EnemyFact> enemies) {
        return enemies.stream()
                .mapToDouble(enemy -> enemy.count() * enemy.hpFraction())
                .sum();
    }
}
