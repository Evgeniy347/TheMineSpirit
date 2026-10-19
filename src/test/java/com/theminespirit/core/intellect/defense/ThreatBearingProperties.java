package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ThreatBearingProperties {
    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(
                        Arbitraries.of(
                                "minecraft:zombie",
                                "minecraft:skeleton",
                                "minecraft:creeper",
                                "minecraft:modded_beast"),
                        Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                        Arbitraries.integers().between(-14, 14),
                        Arbitraries.integers().between(-14, 14),
                        Arbitraries.integers().between(1, 3))
                .as((type, id, x, z, count) -> new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true, count))
                .list()
                .ofMinSize(1)
                .ofMaxSize(6);
    }

    @Property
    void sum_AnyInput_EnemyCountPreserved(@ForAll("enemies") List<EnemyFact> enemies) {
        List<DirectionThreat> threats =
                ThreatBearing.sum(enemies, ThreatBearingTests.CENTER, ThreatBearingTests.PROFILES);

        int input = enemies.stream().mapToInt(EnemyFact::count).sum();
        int sectors = threats.stream().mapToInt(DirectionThreat::enemies).sum();

        assertEquals(
                input,
                sectors,
                "Сумма врагов по секторам равна входному числу: иначе враг потеряется и направление ослабеет");
    }

    @Property
    void sum_AnyInput_StrengthNonNegative(@ForAll("enemies") List<EnemyFact> enemies) {
        List<DirectionThreat> threats =
                ThreatBearing.sum(enemies, ThreatBearingTests.CENTER, ThreatBearingTests.PROFILES);

        for (DirectionThreat threat : threats) {
            assertTrue(
                    threat.strength() >= 0, "Сила сектора неотрицательна: иначе вес направления исказит распределение");
        }
    }

    @Property
    void sum_ShuffledInput_SameResult(@ForAll("enemies") List<EnemyFact> enemies) {
        List<EnemyFact> shuffled = new ArrayList<>(enemies);
        Collections.shuffle(shuffled, new Random(11));

        assertEquals(
                ThreatBearing.sum(enemies, ThreatBearingTests.CENTER, ThreatBearingTests.PROFILES),
                ThreatBearing.sum(shuffled, ThreatBearingTests.CENTER, ThreatBearingTests.PROFILES),
                "Сила направлений не зависит от порядка входа: иначе посты мигают при каждом новом снимке");
    }
}
