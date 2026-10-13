package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class WorkAllocatorProperties {
    private static final List<WorkTarget> CIRCLE_TARGETS = List.of(
            new WorkTarget(WorkKind.TREE, new BlockCoord(10, 64, 0)),
            new WorkTarget(WorkKind.TREE, new BlockCoord(0, 64, 10)),
            new WorkTarget(WorkKind.TREE, new BlockCoord(-10, 64, 0)),
            new WorkTarget(WorkKind.TREE, new BlockCoord(0, 64, -10)));

    @Provide
    Arbitrary<List<SpiritSnapshot>> freeSpirits() {
        return freeSpirit().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<WorkTarget>> targets() {
        return Arbitraries.integers().between(-50, 50)
                .flatMap(x -> Arbitraries.integers().between(-50, 50)
                        .map(z -> new WorkTarget(WorkKind.TREE, new BlockCoord(x, 64, z))))
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }

    @Property
    void allocate_AnyFreeSpiritsAndTargets_EachSpiritOnce(@ForAll("freeSpirits") List<SpiritSnapshot> free,
            @ForAll("targets") List<WorkTarget> targets) {
        List<WorkAssignment> assignments = new WorkAllocator().allocate(free, targets, new WorkReservations(), 100);

        assertEquals(assignments.stream().map(WorkAssignment::spirit).distinct().count(), assignments.size(),
                "Каждый свободный дух появляется в назначениях не больше раза: иначе дух получил бы две цели");
        assertTrue(free.stream().map(SpiritSnapshot::id).toList()
                        .containsAll(assignments.stream().map(WorkAssignment::spirit).toList()),
                "Назначенный дух — из свободных: иначе работа ушла тому, кого нет в списке");
    }

    @Property
    void allocate_AnyFreeSpiritsAndTargets_NoDoubleBookingOfReservedTarget(
            @ForAll("freeSpirits") List<SpiritSnapshot> free, @ForAll("targets") List<WorkTarget> targets) {
        WorkReservations reservations = new WorkReservations();
        for (int index = 0; index < Math.min(free.size(), targets.size()); index += 2) {
            reservations.reserve(targets.get(index), free.get(index).id(), 100, 10000);
        }

        List<WorkAssignment> assignments = new WorkAllocator().allocate(free, targets, reservations, 100);

        for (WorkAssignment assignment : assignments) {
            assertTrue(reservations.owner(assignment.target()).map(owner -> free.stream()
                            .anyMatch(spirit -> spirit.id().equals(owner))).orElse(true),
                    "Цель под чужой бронью не назначается: иначе два духа работают на одной цели");
        }
        Map<WorkTarget, Long> perTarget = new HashMap<>();
        for (WorkAssignment assignment : assignments) {
            perTarget.merge(assignment.target(), 1L, Long::sum);
        }
        for (Map.Entry<WorkTarget, Long> entry : perTarget.entrySet()) {
            Optional<UUID> owner = reservations.owner(entry.getKey());
            if (owner.isPresent() && free.stream().anyMatch(spirit -> spirit.id().equals(owner.get()))) {
                assertEquals(1L, entry.getValue(),
                        "Цель под брони свободного духа занята не больше чем одним: иначе бронь не держит");
            }
        }
    }

    @Property
    void allocate_EqualDistancesMoreTargetsThanSpirits_MaxOnePerTarget(
            @ForAll @IntRange(min = 1, max = 4) int spiritCount) {
        List<SpiritSnapshot> free = IntStream.range(0, spiritCount)
                .mapToObj(index -> new SpiritSnapshot(UUID.nameUUIDFromBytes(new byte[] {(byte) index }),
                        new Point(0, 64, 0), 1, SpiritRole.WORKER, CombatMode.PASSIVE, 0, false,
                                Optional.empty(), 10))
                .toList();

        List<WorkAssignment> assignments = new WorkAllocator().allocate(free, CIRCLE_TARGETS,
                new WorkReservations(), 100);

        assertEquals(spiritCount, assignments.size(), "При достатке целей каждый свободный дух получает работу");
        assertEquals(assignments.stream().map(WorkAssignment::target).distinct().count(), assignments.size(),
                "При равных расстояниях и достатке целей максимум один дух на цель: иначе толпа у одного дерева");
    }

    @Property
    void allocate_AnyInput_Deterministic(@ForAll("freeSpirits") List<SpiritSnapshot> free,
            @ForAll("targets") List<WorkTarget> targets) {
        WorkReservations reservations = new WorkReservations();

        assertEquals(new WorkAllocator().allocate(free, targets, reservations, 100),
                new WorkAllocator().allocate(free, targets, reservations, 100),
                "Тот же вход даёт тот же выход: назначения стабильны между пересборками плана");
    }

    private Arbitrary<SpiritSnapshot> freeSpirit() {
        return Arbitraries.of(SpiritRole.WORKER, SpiritRole.IDLE, SpiritRole.BUILDING)
                .flatMap(role -> Arbitraries.integers().between(-30, 30)
                        .flatMap(x -> Arbitraries.integers().between(0, 3)
                                .map(threats -> new SpiritSnapshot(UUID.randomUUID(), new Point(x, 64, x),
                                        threats / 4.0, role, CombatMode.PASSIVE, threats, false,
                                        Optional.empty(), threats))));
    }
}
