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

class CoordinatorPlanTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Point BASE = new Point(0, 64, 0);

    private static SpiritSnapshot defender(UUID id, boolean attacked) {
        return new SpiritSnapshot(
                id,
                BASE,
                1,
                SpiritRole.DEFENDER,
                CombatMode.GUARD_AREA,
                attacked ? 1 : 0,
                attacked,
                Optional.empty(),
                100,
                1.0);
    }

    private static SpiritSnapshot worker(UUID id, double x, WorkTarget work) {
        return new SpiritSnapshot(
                id,
                new Point(x, 64, 0),
                1,
                SpiritRole.WORKER,
                CombatMode.PASSIVE,
                0,
                false,
                Optional.ofNullable(work),
                100,
                1.0);
    }

    private static WorkTarget tree(int x) {
        return new WorkTarget(WorkKind.TREE, new BlockCoord(x, 64, 0));
    }

    @Test
    void plan_NoDefendedPoint_DefenseAndAssistEmpty() {
        CoordinatorState state =
                new CoordinatorState(Optional.empty(), 0, List.of(defender(ALPHA, false), defender(BETA, true)), 100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertTrue(plan.defense().isEmpty(), "Без защищаемой точки обороны нет: сектора не строятся");
        assertTrue(plan.assists().isEmpty(), "Без защищаемой точки помощи нет: защитников не из кого выбирать");
    }

    @Test
    void plan_DefendedPointWithDefenders_AllDefendersInDefenseAndInRadius() {
        CoordinatorState state = new CoordinatorState(
                Optional.of(BASE),
                16,
                List.of(defender(ALPHA, false), defender(BETA, false), defender(GAMMA, false)),
                100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertEquals(3, plan.defense().size(), "Все защитники в обороне");
        assertEquals(
                List.of(ALPHA, BETA, GAMMA),
                plan.defense().stream()
                        .map(DefenseAssignment::defender)
                        .sorted()
                        .toList(),
                "В обороне именно защитники состояния");
        for (DefenseAssignment assignment : plan.defense()) {
            assertEquals(
                    16,
                    assignment.post().distanceTo(BASE),
                    1e-6,
                    "Пост в радиусе обороны: защитник не уходит за её пределы");
        }
    }

    @Test
    void plan_AttackedSpirit_GetsHelper() {
        CoordinatorState state =
                new CoordinatorState(Optional.of(BASE), 16, List.of(defender(ALPHA, false), defender(BETA, true)), 100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertEquals(1, plan.assists().size(), "Атакованный получает помощника");
        AssistOrder order = plan.assists().get(0);
        assertEquals(BETA, order.ward(), "Подопечный — атакованный");
        assertEquals(ALPHA, order.helper(), "Помощник — свободный защитник из обороны");
    }

    @Test
    void plan_FreeWorkers_GetDifferentTargets() {
        CoordinatorState state = new CoordinatorState(
                Optional.of(BASE),
                16,
                List.of(defender(ALPHA, false), worker(BETA, 1, tree(5)), worker(GAMMA, 9, tree(20))),
                100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertEquals(2, plan.work().size(), "Оба свободных работника получают работу");
        assertEquals(
                2,
                plan.work().stream().map(WorkAssignment::target).distinct().count(),
                "Работники получают разные цели: иначе оба идут к одному дереву");
    }

    @Test
    void plan_DefenderOnPost_GetsNoWork() {
        CoordinatorState state = new CoordinatorState(Optional.of(BASE), 16, List.of(defender(ALPHA, false)), 100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertTrue(plan.work().isEmpty(), "Защитник не получает работу одновременно с обороной: он держит сектор");
    }

    @Test
    void plan_SpiritsWithoutWorkTargets_GetNoWork() {
        CoordinatorState state = new CoordinatorState(
                Optional.of(BASE), 16, List.of(defender(ALPHA, false), worker(BETA, 1, null)), 100);

        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertTrue(plan.work().isEmpty(), "Целей в состоянии нет — работ нет: координатор не выдумывает цели");
    }

    @Test
    void plan_HelpCallWithoutDefendedPoint_NearestFreeSpiritGetsUrgentAssist() {
        UUID caller = UUID.fromString("44444444-4444-4444-4444-444444444444");
        SpiritSnapshot calling = new SpiritSnapshot(
                caller, BASE, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 1, true, Optional.empty(), 100, 1.0);
        SpiritSnapshot near = new SpiritSnapshot(
                ALPHA,
                new Point(4, 64, 0),
                1,
                SpiritRole.IDLE,
                CombatMode.DEFENSIVE,
                0,
                false,
                Optional.empty(),
                100,
                1.0);
        SpiritSnapshot busy = new SpiritSnapshot(
                BETA,
                new Point(25, 64, 0),
                1,
                SpiritRole.IDLE,
                CombatMode.DEFENSIVE,
                1,
                true,
                Optional.empty(),
                100,
                1.0);
        SpiritSnapshot far = new SpiritSnapshot(
                GAMMA,
                new Point(60, 64, 0),
                1,
                SpiritRole.IDLE,
                CombatMode.DEFENSIVE,
                0,
                false,
                Optional.empty(),
                100,
                1.0);
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0, List.of(calling, near, busy, far), 100);

        CoordinatorPlan plan = new Coordinator()
                .plan(state, new WorkReservations(), 100, List.of(new HelpCall(caller, BASE, 100)), 32);

        assertEquals(
                List.of(new AssistOrder(ALPHA, caller, true)),
                plan.assists(),
                "Зов слышит ближайший свободный дух в радиусе R3, даже если дома нет; занятый и далёкий не идут, "
                        + "а приказ помечен срочным для возврата к работе после боя");
    }

    @Test
    void plan_NullState_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Coordinator().plan(null, new WorkReservations(), 100),
                "План строится из состояния: состояние обязательно");
    }

    @Test
    void plan_NullReservations_Throws() {
        CoordinatorState state = new CoordinatorState(Optional.of(BASE), 16, List.of(), 100);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Coordinator().plan(state, null, 100),
                "Брони без реестра не проверить: реестр обязателен");
    }

    @Test
    void plan_NegativeNow_Throws() {
        CoordinatorState state = new CoordinatorState(Optional.of(BASE), 16, List.of(), 100);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Coordinator().plan(state, new WorkReservations(), -1),
                "Время пересборки назад противоречит плану: просрочка броней считается от него");
    }
}
