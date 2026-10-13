package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Comparator;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class StrengthAssessorProperties {
    @Provide
    Arbitrary<List<SpiritSnapshot>> spirits() {
        Arbitrary<SpiritRole> roles = Arbitraries.of(SpiritRole.DEFENDER, SpiritRole.WORKER, SpiritRole.IDLE);
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(1, 20).map(step -> step / 20.0), roles,
                Arbitraries.integers().between(-6, 6), Arbitraries.integers().between(0, 3))
                .as((id, hp, role, x, threats) -> new SpiritSnapshot(id, new Point(x, 64, 0), hp, role,
                        CombatMode.DEFENSIVE, threats, false, Optional.empty(), 0))
                .list().ofMinSize(1).ofMaxSize(5).map(list -> list.stream()
                        .collect(Collectors.toMap(SpiritSnapshot::id, item -> item, (first, second) -> first,
                                LinkedHashMap::new))
                        .values().stream().sorted(Comparator.comparing(SpiritSnapshot::id)).toList());
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(Arbitraries.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"),
                Arbitraries.create(UUID::randomUUID).map(UUID::toString), Arbitraries.integers().between(-8, 8),
                Arbitraries.integers().between(1, 8).map(step -> step / 8.0),
                Arbitraries.integers().between(1, 2))
                .as((type, id, x, hp, count) -> new EnemyFact(type, id, new Point(x, 64, 0), hp, 0, true, true,
                        count))
                .list().ofMinSize(1).ofMaxSize(4);
    }

    @Property
    void assess_AnyInput_OneVerdictPerSpirit(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll boolean weaponAvailable) {
        List<StrengthVerdict> verdicts = StrengthAssessor.assess(spirits, enemies,
                StrengthAssessorTests.PROFILES, StrengthAssessorTests.ARMOR, weaponAvailable);

        List<UUID> owners = verdicts.stream().map(StrengthVerdict::spirit).toList();
        assertEquals(new HashSet<>(owners).size(), owners.size(),
                "Не больше одного вердикта на духа: иначе дух получит и бой, и бегство сразу");
        assertTrue(verdicts.stream().filter(v -> v.action() == StrengthAction.FIGHT).count() <= 3,
                "Бойцов не больше предела: иначе оборона оголяет поселение ради толпы у одного врага");
    }

    @Property
    void assess_AnyInput_Deterministic(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll boolean weaponAvailable) {
        assertEquals(
                StrengthAssessor.assess(spirits, enemies, StrengthAssessorTests.PROFILES,
                        StrengthAssessorTests.ARMOR, weaponAvailable),
                StrengthAssessor.assess(spirits, enemies, StrengthAssessorTests.PROFILES,
                        StrengthAssessorTests.ARMOR, weaponAvailable),
                "Тот же вход даёт те же вердикты: иначе приказы мигают между пересборками");
    }

    @Property
    void assess_AnyInput_EnemyFromInputAndNearest(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("enemies") List<EnemyFact> enemies) {
        List<StrengthVerdict> verdicts = StrengthAssessor.assess(spirits, enemies,
                StrengthAssessorTests.PROFILES, StrengthAssessorTests.ARMOR, false);
        Set<String> ids = new HashSet<>();
        for (EnemyFact enemy : enemies) {
            ids.add(enemy.id());
        }

        for (StrengthVerdict verdict : verdicts) {
            if (verdict.action() != StrengthAction.FIGHT && verdict.action() != StrengthAction.FLEE) {
                continue;
            }
            assertTrue(verdict.enemy().isPresent(),
                    "Боевой вердикт указывает на врага: иначе приказ уйдёт без цели");
            assertTrue(ids.contains(verdict.enemy().orElseThrow().toString()),
                    "Враг вердикта — из входа: иначе приказ уйдёт на несуществующую цель");
            SpiritSnapshot spirit = spirits.stream().filter(s -> s.id().equals(verdict.spirit())).findFirst()
                    .orElseThrow();
            assertEquals(nearestId(spirit, enemies), verdict.enemy().orElseThrow().toString(),
                    "Защитник бьёт ближайшего врага: иначе дух бежит через карту мимо ближней угрозы");
        }
    }

    private static String nearestId(SpiritSnapshot spirit, List<EnemyFact> enemies) {
        String best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (EnemyFact enemy : enemies) {
            double distance = enemy.pos().distanceTo(spirit.pos());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && enemy.id().compareTo(best) < 0)) {
                best = enemy.id();
                bestDistance = distance;
            }
        }
        return best;
    }
}
