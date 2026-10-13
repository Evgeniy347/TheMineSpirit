package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoordinatorStateTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static SpiritSnapshot spirit(UUID id, SpiritRole role) {
        return new SpiritSnapshot(id, new Point(0, 64, 0), 1, role, CombatMode.DEFENSIVE, 0, false, Optional.empty(),
                10);
    }

    @Test
    void state_NoSpirits_ReturnsEmptyCollections() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0, List.of(), 0);

        assertEquals(List.of(), state.spirits(),
                "Пустое состояние не прячет духов: план обязан видеть, что координировать некого");
        assertEquals(List.of(), state.defenders(), "Пустое состояние: защитников нет");
        assertEquals(List.of(), state.workers(), "Пустое состояние: работников нет");
        assertTrue(state.byId(ALPHA).isEmpty(), "Пустое состояние: дух по id не находится");
    }

    @Test
    void defendedPoint_Empty_DefensePointIsEmpty() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0,
                List.of(spirit(ALPHA, SpiritRole.DEFENDER)), 0);

        assertTrue(state.defendedPoint().isEmpty(),
                "Без защищаемой точки обороны нет: план не строит сектора и не шлёт помощь");
        assertEquals(List.of(spirit(ALPHA, SpiritRole.DEFENDER)), state.defenders(),
                "Духи с ролью защитника видны в состоянии независимо от точки: их участь решает план");
    }

    @Test
    void defendedPoint_PresentNonPositiveRadius_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CoordinatorState(Optional.of(new Point(0, 64, 0)), 0, List.of(), 0),
                "Радиус обороны нулевой или отрицательный при точке вырождает сектора: посты слипнутся в центр");

        assertTrue(error.getMessage().contains("defendedRadius"),
                "Сообщение называет поле defendedRadius: без этого при падении не видно, что не так с радиусом");
    }

    @Test
    void constructor_NullDefendedPoint_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new CoordinatorState(null, 0, List.of(), 0),
                "null вместо Optional точки обороны не говорит, есть ли оборона: передавайте Optional.empty()");
    }

    @Test
    void constructor_NullSpirits_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new CoordinatorState(Optional.empty(), 0, null, 0),
                "null вместо списка духов не говорит, пустое состояние или нет: передавайте пустой список");
    }

    @Test
    void spirits_MixedIds_AreSortedById() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0,
                List.of(spirit(GAMMA, SpiritRole.IDLE), spirit(ALPHA, SpiritRole.WORKER), spirit(BETA,
                        SpiritRole.BUILDING)), 0);

        assertEquals(List.of(ALPHA, BETA, GAMMA),
                state.spirits().stream().map(SpiritSnapshot::id).toList(),
                "Духи упорядочены по id: план обязан быть детерминирован при том же входе независимо от порядка "
                        + "наблюдения");
    }

    @Test
    void byId_ExistingSpirit_IsFound() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0,
                List.of(spirit(BETA, SpiritRole.IDLE), spirit(ALPHA, SpiritRole.IDLE)), 0);

        assertEquals(Optional.of(spirit(ALPHA, SpiritRole.IDLE)), state.byId(ALPHA),
                "Дух из состояния находится по id: без этого план не сопоставит решения с духами");
    }

    @Test
    void byId_MissingSpirit_IsEmpty() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0, List.of(spirit(ALPHA, SpiritRole.IDLE)),
                0);

        assertTrue(state.byId(BETA).isEmpty(), "Дух вне состояния не находится: план не знает несуществующих");
    }

    @Test
    void defenders_OnlyDefenderRole_AreReturned() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0,
                List.of(spirit(ALPHA, SpiritRole.DEFENDER), spirit(BETA, SpiritRole.WORKER), spirit(GAMMA,
                        SpiritRole.DEFENDER)), 0);

        assertEquals(List.of(ALPHA, GAMMA), state.defenders().stream().map(SpiritSnapshot::id).toList(),
                "Защитники — только роль DEFENDER: работник в сектор не встанет");
    }

    @Test
    void workers_OnlyWorkerRole_AreReturned() {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0,
                List.of(spirit(ALPHA, SpiritRole.WORKER), spirit(BETA, SpiritRole.BUILDING), spirit(GAMMA,
                        SpiritRole.DEFENDER)), 0);

        assertEquals(List.of(ALPHA), state.workers().stream().map(SpiritSnapshot::id).toList(),
                "Работники — только роль WORKER: строитель и защитник цели работ не получают");
    }

    @Test
    void tick_Negative_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new CoordinatorState(Optional.empty(), 0, List.of(), -1),
                "Отрицательный тик состояния противоречит наблюдению: просроченность снимка считается от него");
    }
}
