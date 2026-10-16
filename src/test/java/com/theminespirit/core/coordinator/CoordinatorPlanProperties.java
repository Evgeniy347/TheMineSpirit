package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CoordinatorPlanProperties {
    private static final Point BASE = new Point(0, 64, 0);

    @Provide
    Arbitrary<CoordinatorState> states() {
        return spirit().list().ofMinSize(0).ofMaxSize(6)
                .map(spirits -> new CoordinatorState(Optional.of(BASE), 16, spirits, 100));
    }

    @Property
    void plan_AnyState_Deterministic(@ForAll("states") CoordinatorState state) {
        WorkReservations reservations = new WorkReservations();

        assertEquals(new Coordinator().plan(state, reservations, 100),
                new Coordinator().plan(state, reservations, 100),
                "Тот же вход даёт тот же план: решения стабильны между пересборками");
    }

    @Property
    void plan_AnyState_DefendersWithinRadius(@ForAll("states") CoordinatorState state) {
        CoordinatorPlan plan = new Coordinator().plan(state, new WorkReservations(), 100);

        assertEquals(16, state.defendedRadius(), "Радиус состояния переносится в план как есть");
        for (DefenseAssignment assignment : plan.defense()) {
            assertEquals(16, assignment.post().distanceTo(BASE), 1e-6,
                    "Каждый пост в радиусе обороны: защитник не уходит за её пределы");
        }
        for (DefenseAssignment assignment : plan.defense()) {
            assertTrue(state.defenders().stream().anyMatch(defender -> defender.id().equals(assignment.defender())),
                    "Пост получает защитник состояния: чужой в оборону не попадает");
        }
    }

    @Property
    void plan_AnyState_ValidAfterSpiritLoss(@ForAll("states") CoordinatorState state) {
        CoordinatorPlan before = new Coordinator().plan(state, new WorkReservations(), 100);
        List<SpiritSnapshot> remaining = state.spirits().size() > 1
                ? state.spirits().subList(1, state.spirits().size())
                : state.spirits();
        CoordinatorState afterState = new CoordinatorState(state.defendedPoint(), state.defendedRadius(),
                remaining, state.tick());

        CoordinatorPlan after = new Coordinator().plan(afterState, new WorkReservations(), 100);

        assertEquals(new Coordinator().plan(afterState, new WorkReservations(), 100), after,
                "После выбытия духа оставшиеся решения снова детерминированы");
        for (DefenseAssignment assignment : after.defense()) {
            assertEquals(16, assignment.post().distanceTo(BASE), 1e-6,
                    "После выбытия оставшиеся посты в радиусе: перераспределение не выгоняет защитников");
        }
        for (AssistOrder order : after.assists()) {
            assertTrue(afterState.spirits().stream().anyMatch(spirit -> spirit.id().equals(order.helper())),
                    "После выбытия помощник — из оставшихся духов");
            assertTrue(afterState.spirits().stream().anyMatch(spirit -> spirit.id().equals(order.ward())),
                    "После выбытия подопечный — из оставшихся духов");
        }
        assertEquals(before.defendedPoint(), after.defendedPoint(), "Точка обороны не меняется от выбытия духа");
    }

    private Arbitrary<SpiritSnapshot> spirit() {
        return Arbitraries.of(SpiritRole.DEFENDER, SpiritRole.WORKER, SpiritRole.IDLE, SpiritRole.BUILDING)
                .flatMap(role -> Arbitraries.integers().between(0, 3)
                        .flatMap(threats -> Arbitraries.of(true, false)
                                .filter(attacked -> !attacked || threats > 0)
                                .flatMap(attacked -> Arbitraries.integers().between(-20, 20)
                                        .map(x -> new SpiritSnapshot(UUID.randomUUID(), new Point(x, 64, x),
                                                threats / 4.0, role, CombatMode.DEFENSIVE, threats, attacked,
                                                role != SpiritRole.DEFENDER && threats == 0
                                                        ? Optional.of(new WorkTarget(WorkKind.TREE,
                                                                new BlockCoord(x * 3, 64, 0)))
                                                        : Optional.empty(),
                                                threats + 1, 1.0)))));
    }
}
