package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpeditionPlannerTests {
    private static final Point MINE = new Point(100, 40, 100);
    private static final ExpeditionPlanner PLANNER = new ExpeditionPlanner();

    private static SettlementView needy() {
        return SettlementViews.settlement(2, 2, 0, 0, 0, 0, 0.2);
    }

    private static SpiritSnapshot workerAt(UUID id, double x) {
        return new SpiritSnapshot(id, new Point(x, 64, 0), 1, SpiritRole.WORKER, CombatMode.DEFENSIVE, 0, false,
                Optional.empty(), 0, 1.0);
    }

    private static UUID uuid(int last) {
        return UUID.fromString("11111111-1111-1111-1111-11111111111" + last);
    }

    @Test
    void plan_NoNeed_Empty() {
        SettlementView rich = SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.2);

        assertEquals(Optional.empty(),
                PLANNER.plan(rich, List.of(workerAt(uuid(1), 0), workerAt(uuid(2), 1)), MINE),
                "Без нужды в руде экспедиции нет: иначе духи уйдут из полного поселения");
    }

    @Test
    void plan_OneSuitable_Empty() {
        assertEquals(Optional.empty(), PLANNER.plan(needy(), List.of(workerAt(uuid(1), 0)), MINE),
                "В одиночку в шахту не ходят: команда меньше двух не собирается");
    }

    @Test
    void plan_FiveCandidates_ThreeNearest() {
        List<SpiritSnapshot> candidates = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            candidates.add(workerAt(uuid(index), index * 10));
        }

        Expedition expedition = PLANNER.plan(needy(), candidates, MINE).orElseThrow();

        assertEquals(3, expedition.team().size(),
                "Команда больше трёх не собирается: иначе шахта превратится в толпу");
        assertEquals(List.of(uuid(5), uuid(4), uuid(3)), expedition.team(),
                "Берутся трое ближайших к шахте: дальним идти дольше, их труд нужнее дома");
        assertEquals(MINE, expedition.target(), "Цель экспедиции — известная шахта: иначе команда идёт вслепую");
        assertEquals(100, expedition.startedAt(),
                "Старт экспедиции — тик обстановки: иначе повторные планы задвоят команду");
    }

    @Test
    void plan_DefenderAmongCandidates_Excluded() {
        SpiritSnapshot guard = new SpiritSnapshot(uuid(9), new Point(1, 64, 1), 1, SpiritRole.DEFENDER,
                CombatMode.DEFENSIVE, 0, false, Optional.empty(), 0, 1.0);

        Expedition expedition =
                PLANNER.plan(needy(), List.of(workerAt(uuid(1), 0), workerAt(uuid(2), 1), guard), MINE).orElseThrow();

        assertEquals(List.of(uuid(2), uuid(1)), expedition.team(),
                "Защитник в команду не берётся: пост он держит, кирка — дело работника с неё");
    }

    @Test
    void plan_NullInputs_Throw() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PLANNER.plan(null, List.of(), MINE),
                "Экспедиция без обстановки не планируется: нужда — из запасов");

        assertTrue(error.getMessage().contains("обстановки"),
                "Сообщение называет причину: при падении видно, чего не хватает планировщику");
        assertThrows(IllegalArgumentException.class, () -> PLANNER.plan(needy(), null, MINE),
                "Экспедиция без кандидатов не планируется: команда — из духов");
        assertThrows(IllegalArgumentException.class, () -> PLANNER.plan(needy(), List.of(), null),
                "Экспедиция без цели не планируется: шахта обязана быть известна");
    }

    @Test
    void expedition_BadTeam_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new Expedition(List.of(uuid(1)), MINE, 0),
                "Команда из одного в шахту не идёт: минимум двое");
        assertThrows(IllegalArgumentException.class,
                () -> new Expedition(List.of(uuid(1), uuid(2), uuid(3), uuid(4)), MINE, 0),
                "Команда из четырёх в шахту не идёт: максимум трое");
    }

    @Test
    void expedition_ImmutableTeam_Accepted() {
        Expedition expedition = new Expedition(List.of(uuid(1), uuid(2)), MINE, 0);

        assertEquals(List.of(uuid(1), uuid(2)), expedition.team(),
                "Команда в иммутабельном списке принимается: иначе любой вызов с List.of роняет поход "
                        + "с NullPointerException вместо похода, а GameTest приглашения не собирается");
    }

    @Test
    void expedition_NullMember_Throws() {
        List<UUID> team = new ArrayList<>(List.of(uuid(1), uuid(2)));
        team.set(1, null);

        assertThrows(IllegalArgumentException.class, () -> new Expedition(team, MINE, 0),
                "Команда с null-духом не идёт: каждый участник — конкретный дух");
    }
}
