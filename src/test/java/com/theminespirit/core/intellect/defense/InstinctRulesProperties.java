package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class InstinctRulesProperties {
    @Provide
    Arbitrary<DefenderCapability> selves() {
        return Combinators.combine(
                        Arbitraries.create(UUID::randomUUID),
                        Arbitraries.integers().between(-10, 10).map(x -> new Point(x, 64, -x)),
                        Arbitraries.doubles().between(0.0, 1.0),
                        Arbitraries.integers().between(0, 20),
                        Arbitraries.of(true, false),
                        Arbitraries.doubles().between(0.0, 12.0),
                        Arbitraries.of(true, false))
                .as((id, pos, hp, armor, bow, dps, heal) -> new DefenderCapability(id, pos, hp, armor, bow, dps, heal));
    }

    @Provide
    Arbitrary<RankedTarget> targets() {
        return Combinators.combine(
                        Arbitraries.of(
                                "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider"),
                        Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                        Arbitraries.integers().between(-10, 10),
                        Arbitraries.integers().between(-10, 10))
                .as((type, id, x, z) -> {
                    EnemyFact enemy = new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true);
                    return new RankedTarget(
                            enemy, DefenseProfileTests.PROFILE.classify(type), 0, Math.abs(x) + Math.abs(z));
                });
    }

    @Provide
    Arbitrary<List<DefenderCapability>> allies() {
        return selves().list().ofMinSize(0).ofMaxSize(3).map(InstinctRulesProperties::distinctIds);
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(
                        Arbitraries.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"),
                        Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                        Arbitraries.integers().between(-10, 10),
                        Arbitraries.integers().between(-10, 10))
                .as((type, id, x, z) -> new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true))
                .list()
                .ofMinSize(0)
                .ofMaxSize(3);
    }

    @Property
    void decide_AnyInput_Deterministic(
            @ForAll("selves") DefenderCapability self,
            @ForAll("targets") RankedTarget target,
            @ForAll("enemies") List<EnemyFact> enemies,
            @ForAll("allies") List<DefenderCapability> allies) {
        assertEquals(
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE),
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Стойка детерминирована: иначе боец мечется между стойками на одной обстановке");
    }

    @Property
    void decide_AnyInput_HoldDistancePositiveWhenPresent(
            @ForAll("selves") DefenderCapability self,
            @ForAll("targets") RankedTarget target,
            @ForAll("enemies") List<EnemyFact> enemies,
            @ForAll("allies") List<DefenderCapability> allies) {
        InstinctDecision decision =
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE);

        decision.holdDistance()
                .ifPresent(distance ->
                        assertTrue(distance > 0, "Держание положительно: иначе отскок ведёт внутрь радиуса взрыва"));
    }

    @Property
    void decide_AnyInput_HandOffNeverSelf(
            @ForAll("selves") DefenderCapability self,
            @ForAll("targets") RankedTarget target,
            @ForAll("enemies") List<EnemyFact> enemies,
            @ForAll("allies") List<DefenderCapability> allies) {
        InstinctDecision decision =
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE);

        decision.handOff()
                .ifPresent(next -> assertTrue(
                        !next.equals(self.id()), "Приёмник — не уходящий: иначе передача цели замкнётся на раненого"));
    }

    @Property
    void decide_AnyInput_ReasonNonBlank(
            @ForAll("selves") DefenderCapability self,
            @ForAll("targets") RankedTarget target,
            @ForAll("enemies") List<EnemyFact> enemies,
            @ForAll("allies") List<DefenderCapability> allies) {
        InstinctDecision decision =
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE);

        assertTrue(!decision.reason().isBlank(), "Причина решения непуста: иначе trace стойки не объяснит приказ");
    }

    @Property
    void decide_HealthyMeleeVsCreeper_StrikeAndRetreat(
            @ForAll("allies") List<DefenderCapability> allies, @ForAll("enemies") List<EnemyFact> enemies) {
        DefenderCapability self =
                new DefenderCapability(UUID.randomUUID(), new Point(0, 64, 0), 0.8, 16, false, 6.0, false);
        EnemyFact creeper = new EnemyFact(
                "minecraft:creeper", UUID.randomUUID().toString(), new Point(6, 64, 0), 1.0, 0, true, true);
        RankedTarget target = new RankedTarget(creeper, TargetClass.CREEPER, 0, 6.0);

        InstinctDecision decision =
                InstinctRules.decide(self, target, enemies, allies, Set.of(), DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.STRIKE_AND_RETREAT,
                decision.stance(),
                "Здоровый мечник против крипера идёт наскоком: иначе либо стоит впритык, либо не бьёт вовсе");
    }

    private static List<DefenderCapability> distinctIds(List<DefenderCapability> allies) {
        return allies.stream()
                .collect(Collectors.toMap(
                        DefenderCapability::id, item -> item, (first, second) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(DefenderCapability::id))
                .toList();
    }
}
