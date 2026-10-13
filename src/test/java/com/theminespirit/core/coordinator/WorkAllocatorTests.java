package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkAllocatorTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static SpiritSnapshot free(UUID id, double x) {
        return new SpiritSnapshot(id, new Point(x, 64, 0), 1, SpiritRole.WORKER, CombatMode.PASSIVE, 0, false,
                Optional.empty(), 10);
    }

    private static WorkTarget target(int x, int z) {
        return new WorkTarget(WorkKind.TREE, new BlockCoord(x, 64, z));
    }

    @Test
    void allocate_OneTargetTwoSpirits_BothGetIt() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget tree = target(10, 0);

        List<WorkAssignment> assignments = new WorkAllocator().allocate(List.of(free(ALPHA, 0), free(BETA, 1)),
                List.of(tree), reservations, 100);

        assertEquals(2, assignments.size(),
                "Работа есть всем при нехватке целей: свободный дух не пустует, когда цель одна");
        assertTrue(assignments.stream().allMatch(assignment -> assignment.target().equals(tree)),
                "Оба духа получают единственную цель: запрета на толпу нет");
    }

    @Test
    void allocate_ThreeTargetsThreeSpirits_DifferentTargets() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget first = target(1, 0);
        WorkTarget second = target(5, 0);
        WorkTarget third = target(9, 0);

        List<WorkAssignment> assignments = new WorkAllocator().allocate(List.of(free(ALPHA, 0), free(BETA, 4),
                free(GAMMA, 8)), List.of(first, second, third), reservations, 100);

        assertEquals(3, assignments.size(), "Каждый свободный дух получает цель");
        assertEquals(3, assignments.stream().map(WorkAssignment::target).distinct().count(),
                "Духи у разных расстояний берут разные цели: иначе все толпятся у одной");
    }

    @Test
    void allocate_TargetUnderForeignReservation_Skipped() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget tree = target(10, 0);
        reservations.reserve(tree, GAMMA, 100, 1000);

        List<WorkAssignment> assignments = new WorkAllocator().allocate(List.of(free(ALPHA, 0), free(BETA, 1)),
                List.of(tree), reservations, 100);

        assertTrue(assignments.isEmpty(),
                "Цель под чужой бронью не назначается: иначе два духа работают на одном дереве");
    }

    @Test
    void allocate_NoFreeSpirits_ReturnsEmpty() {
        assertTrue(new WorkAllocator().allocate(List.of(), List.of(target(1, 0)), new WorkReservations(), 100)
                .isEmpty(), "Свободных нет — назначений нет");
    }

    @Test
    void allocate_NoCandidates_ReturnsEmpty() {
        assertTrue(new WorkAllocator().allocate(List.of(free(ALPHA, 0)), List.of(), new WorkReservations(), 100)
                .isEmpty(), "Целей нет — назначений нет");
    }

    @Test
    void allocate_EqualDistanceTie_BrokenByAnchor() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget west = target(10, 0);
        WorkTarget east = target(0, 10);

        List<WorkAssignment> assignments = new WorkAllocator().allocate(List.of(free(ALPHA, 0)),
                List.of(east, west), reservations, 100);

        assertEquals(1, assignments.size(), "Один дух получает одну цель");
        assertEquals(east, assignments.get(0).target(),
                "При равной оценке берётся цель с меньшим якорем по порядку BlockCoord: выбор детерминирован");
    }

    @Test
    void allocate_CrowdPenalty_SplitsSpiritsToDifferentTargets() {
        WorkReservations reservations = new WorkReservations();
        WorkTarget near = target(2, 0);
        WorkTarget far = target(10, 0);

        List<WorkAssignment> assignments = new WorkAllocator().allocate(List.of(free(ALPHA, 0), free(BETA, 3)),
                List.of(near, far), reservations, 100);

        assertEquals(2, assignments.size(), "Оба духа получают работу");
        assertEquals(2, assignments.stream().map(WorkAssignment::target).distinct().count(),
                "Штраф за толпу разводит духов по разным целям: иначе оба пошли на ближайшую");
    }

    @Test
    void allocate_NullFree_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkAllocator().allocate(null, List.of(target(1, 0)), new WorkReservations(), 100),
                "null вместо свободных не говорит, есть ли они: передавайте пустой список");
    }

    @Test
    void allocate_NullCandidates_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkAllocator().allocate(List.of(free(ALPHA, 0)), null, new WorkReservations(), 100),
                "null вместо целей не говорит, есть ли они: передавайте пустой список");
    }

    @Test
    void allocate_NullReservations_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkAllocator().allocate(List.of(free(ALPHA, 0)), List.of(target(1, 0)), null, 100),
                "Брони без реестра не проверить: реестр обязателен");
    }

    @Test
    void allocate_NegativeNow_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkAllocator().allocate(List.of(free(ALPHA, 0)), List.of(target(1, 0)),
                        new WorkReservations(), -1),
                "Время распределения назад противоречит пересборке: просрочка считается от него");
    }
}
