package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TargetRankerProperties {
    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(
                        Arbitraries.of(
                                "minecraft:zombie",
                                "minecraft:skeleton",
                                "minecraft:creeper",
                                "minecraft:spider",
                                "minecraft:phantom",
                                "minecraft:ravager"),
                        Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                        Arbitraries.integers().between(-12, 12),
                        Arbitraries.integers().between(-12, 12))
                .as((type, id, x, z) -> new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true))
                .list()
                .ofMinSize(1)
                .ofMaxSize(6)
                .map(TargetRankerProperties::distinctIds);
    }

    @Property
    void rank_AnyInput_IsPermutationOfInput(
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll DefenderRole role, @ForAll boolean archerPresent) {
        List<RankedTarget> ranked = TargetRanker.rank(role, archerPresent, enemies, DefenseProfileTests.PROFILE);

        List<String> inputIds = enemies.stream().map(EnemyFact::id).sorted().toList();
        List<String> rankedIds =
                ranked.stream().map(target -> target.enemy().id()).sorted().toList();

        assertEquals(
                inputIds,
                rankedIds,
                "Порядок — перестановка входа: каждая цель ровно один раз, иначе цель потеряется или удвоится");
    }

    @Property
    void rank_AnyInput_ClassOrderNeverDecreases(
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll DefenderRole role, @ForAll boolean archerPresent) {
        List<RankedTarget> ranked = TargetRanker.rank(role, archerPresent, enemies, DefenseProfileTests.PROFILE);

        for (int index = 1; index < ranked.size(); index++) {
            assertTrue(
                    ranked.get(index - 1).rank() <= ranked.get(index).rank(),
                    "Ранг класса не убывает вдоль порядка: иначе низкоприоритетная цель вытеснит главную угрозу");
        }
    }

    @Property
    void rank_ShuffledInput_SameOrder(
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll DefenderRole role, @ForAll boolean archerPresent) {
        List<EnemyFact> shuffled = new ArrayList<>(enemies);
        Collections.shuffle(shuffled, new Random(7));

        assertEquals(
                TargetRanker.rank(role, archerPresent, enemies, DefenseProfileTests.PROFILE),
                TargetRanker.rank(role, archerPresent, shuffled, DefenseProfileTests.PROFILE),
                "Порядок не зависит от порядка входа: иначе цели мигают при каждом новом снимке мира");
    }

    private static List<EnemyFact> distinctIds(List<EnemyFact> enemies) {
        return enemies.stream()
                .collect(Collectors.toMap(EnemyFact::id, item -> item, (first, second) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(EnemyFact::id))
                .toList();
    }
}
