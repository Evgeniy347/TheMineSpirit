package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DefensePlanProperties {
    @Provide
    Arbitrary<List<DefenderCapability>> defenders() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(-16, 16).map(x -> new Point(x, 64, -x)),
                Arbitraries.doubles().between(0.0, 1.0), Arbitraries.integers().between(0, 20),
                Arbitraries.of(true, false), Arbitraries.doubles().between(0.0, 12.0),
                Arbitraries.of(true, false))
                .as((id, pos, hp, armor, bow, dps, heal) -> new DefenderCapability(id, pos, hp, armor, bow, dps, heal))
                .list().ofMinSize(1).ofMaxSize(5).map(DefensePlanProperties::distinctIds);
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(
                Arbitraries.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider"),
                Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                Arbitraries.integers().between(-14, 14), Arbitraries.integers().between(-14, 14))
                .as((type, id, x, z) -> new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true))
                .list().ofMinSize(1).ofMaxSize(5).map(DefensePlanProperties::distinctEnemyIds);
    }

    @Provide
    Arbitrary<Point> center() {
        return Combinators.combine(Arbitraries.integers().between(-16, 16), Arbitraries.integers().between(-16, 16))
                .as((x, z) -> new Point(x, 64, z));
    }

    @Property
    void plan_AnyInput_OneOrderPerSpirit(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        DefensePlan plan = plan(center, defenders, enemies);

        List<UUID> owners = plan.orders().stream().map(DefenderOrder::defender).toList();

        assertEquals(new HashSet<>(owners).size(), owners.size(),
                "У духа не больше одного приказа: иначе дух получит и пост, и бой сразу");
    }

    @Property
    void plan_AnyInput_TargetsDistinct(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        DefensePlan plan = plan(center, defenders, enemies);

        List<String> targets = plan.orders().stream().filter(order -> order.target().isPresent())
                .map(order -> order.target().orElseThrow().id()).toList();

        assertEquals(new HashSet<>(targets).size(), targets.size(),
                "Каждая цель занята не больше чем одним духом: иначе двое бьют одного, а второй без присмотра");
    }

    @Property
    void plan_AnyInput_OrdersAndTargetsFromInput(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        DefensePlan plan = plan(center, defenders, enemies);

        Set<UUID> defenderIds =
                defenders.stream().map(DefenderCapability::id).collect(Collectors.toSet());
        Set<String> enemyIds = enemies.stream().map(EnemyFact::id).collect(Collectors.toSet());
        for (DefenderOrder order : plan.orders()) {
            assertTrue(defenderIds.contains(order.defender()),
                    "Приказ ссылается на защитника из входа: иначе приказ уйдёт несуществующему духу");
            order.target().ifPresent(target -> assertTrue(enemyIds.contains(target.id()),
                    "Цель приказа — враг из входа: иначе приказ уйдёт на несуществующую цель"));
        }
    }

    @Property
    void plan_AnyInput_Deterministic(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        assertEquals(plan(center, defenders, enemies), plan(center, defenders, enemies),
                "Тот же вход даёт тот же план: иначе приказы мигают между пересборками");
    }

    @Property
    void plan_ShuffledInput_SamePlan(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        List<DefenderCapability> shuffledDefenders = new ArrayList<>(defenders);
        List<EnemyFact> shuffledEnemies = new ArrayList<>(enemies);
        Collections.shuffle(shuffledDefenders, new Random(31));
        Collections.shuffle(shuffledEnemies, new Random(37));

        assertEquals(plan(center, defenders, enemies), plan(center, shuffledDefenders, shuffledEnemies),
                "План не зависит от порядка входа: иначе приказы мигают при каждом новом снимке мира");
    }

    @Property
    void plan_RemovedDefender_PlanStaysValid(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center) {
        Assume.that(defenders.size() >= 2);
        List<DefenderCapability> remaining = defenders.subList(1, defenders.size());

        DefensePlan reduced = plan(center, remaining, enemies);

        Set<UUID> remainingIds =
                remaining.stream().map(DefenderCapability::id).collect(Collectors.toSet());
        Set<String> enemyIds = enemies.stream().map(EnemyFact::id).collect(Collectors.toSet());
        for (DefenderOrder order : reduced.orders()) {
            assertTrue(remainingIds.contains(order.defender()),
                    "После выбытия приказы только уцелевшим: иначе приказ уйдёт выбывшему духу");
            order.target().ifPresent(target -> assertTrue(enemyIds.contains(target.id()),
                    "После выбытия цели только из входа: иначе приказ уйдёт на несуществующую цель"));
        }
        List<String> targets = reduced.orders().stream().filter(order -> order.target().isPresent())
                .map(order -> order.target().orElseThrow().id()).toList();
        assertEquals(new HashSet<>(targets).size(), targets.size(),
                "После выбытия каждая цель за одним духом: иначе перераспределение дублирует цели");
        for (DefenseAssignment post : reduced.posts()) {
            assertTrue(post.post().distanceTo(center) <= 16.0 + 1e-9,
                    "После выбытия посты внутри радиуса: иначе уцелевшие встанут за периметром");
        }
    }

    private static DefensePlan plan(Point center, List<DefenderCapability> defenders, List<EnemyFact> enemies) {
        CoordinatorState state = new CoordinatorState(Optional.of(center), 16.0, List.of(), 0);
        return DefensePlanner.plan(state, defenders, enemies, ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);
    }

    private static List<DefenderCapability> distinctIds(List<DefenderCapability> defenders) {
        return defenders.stream()
                .collect(Collectors.toMap(DefenderCapability::id, item -> item, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(DefenderCapability::id)).toList();
    }

    private static List<EnemyFact> distinctEnemyIds(List<EnemyFact> enemies) {
        return enemies.stream()
                .collect(Collectors.toMap(EnemyFact::id, item -> item, (first, second) -> first, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(EnemyFact::id)).toList();
    }
}
