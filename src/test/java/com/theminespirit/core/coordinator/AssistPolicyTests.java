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

class AssistPolicyTests {
    private static final UUID WARD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEAR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FAR = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static SpiritSnapshot defender(UUID id, double x, int threats, boolean attacked) {
        return new SpiritSnapshot(id, new Point(x, 64, 0), 1, SpiritRole.DEFENDER, CombatMode.GUARD_AREA, threats,
                attacked, Optional.empty(), 10);
    }

    private static SpiritSnapshot worker(UUID id, double x) {
        return new SpiritSnapshot(id, new Point(x, 64, 0), 1, SpiritRole.WORKER, CombatMode.DEFENSIVE, 0, false,
                Optional.empty(), 10);
    }

    private static DefenseAssignment post(UUID defender, double x) {
        return new DefenseAssignment(defender, new Point(x, 64, 0), 0);
    }

    @Test
    void assist_NoFreeDefenders_SelectsNearest() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot near = defender(NEAR, 2, 0, false);
        SpiritSnapshot far = defender(FAR, 5, 0, false);

        List<UUID> helpers = new AssistPolicy().assist(WARD, List.of(ward, near, far),
                List.of(post(NEAR, 2), post(FAR, 5)), 2);

        assertEquals(List.of(NEAR, FAR), helpers,
                "Выбран ближайший свободный защитник и идёт первым: иначе помощь идёт через весь круг мимо ближнего");
    }

    @Test
    void assist_TwoFreeDefenders_MaxOneHelper() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot near = defender(NEAR, 2, 0, false);
        SpiritSnapshot far = defender(FAR, 5, 0, false);

        List<UUID> helpers = new AssistPolicy().assist(WARD, List.of(ward, near, far),
                List.of(post(NEAR, 2), post(FAR, 5)), 1);

        assertEquals(1, helpers.size(), "Не больше maxHelpers помощников: иначе сектора пустеют");
        assertEquals(List.of(NEAR), helpers, "Среди двух свободных взят ближайший");
    }

    @Test
    void assist_NoDefenders_ReturnsEmpty() {
        SpiritSnapshot ward = worker(WARD, 0);

        assertTrue(new AssistPolicy().assist(WARD, List.of(ward), List.of(), 2).isEmpty(),
                "Защитников нет — помощь не выбирается");
    }

    @Test
    void assist_AttackedDefender_NotAmongHelpers() {
        SpiritSnapshot attacked = defender(WARD, 0, 1, true);
        SpiritSnapshot helper = defender(NEAR, 3, 0, false);

        List<UUID> helpers = new AssistPolicy().assist(WARD, List.of(attacked, helper),
                List.of(post(WARD, 0), post(NEAR, 3)), 2);

        assertEquals(List.of(NEAR), helpers, "Атакованный не помогает себе: он держит свой сектор или отбивается");
        assertTrue(!helpers.contains(WARD), "Атакованный исключён из помощников");
    }

    @Test
    void assist_BusyDefenders_FreeOneChosen() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot busy = defender(NEAR, 2, 1, true);
        SpiritSnapshot free = defender(FAR, 5, 0, false);

        List<UUID> helpers = new AssistPolicy().assist(WARD, List.of(ward, busy, free),
                List.of(post(NEAR, 2), post(FAR, 5)), 2);

        assertEquals(List.of(FAR), helpers,
                "Занятый боем защитник не бросает сектор: помощь идёт от свободного");
    }

    @Test
    void assist_ShuffledInput_SameHelpers() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot near = defender(NEAR, 2, 0, false);
        SpiritSnapshot far = defender(FAR, 5, 0, false);

        List<UUID> first = new AssistPolicy().assist(WARD, List.of(ward, near, far),
                List.of(post(NEAR, 2), post(FAR, 5)), 2);
        List<UUID> second = new AssistPolicy().assist(WARD, List.of(far, ward, near),
                List.of(post(FAR, 5), post(NEAR, 2)), 2);

        assertEquals(first, second, "Порядок входа не меняет выход: помощь не зависит от порядка наблюдения");
    }

    @Test
    void assist_ZeroMaxHelpers_ReturnsEmpty() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot near = defender(NEAR, 2, 0, false);

        assertTrue(new AssistPolicy().assist(WARD, List.of(ward, near), List.of(post(NEAR, 2)), 0).isEmpty(),
                "Нулевой предел — помощь не выбирается: оборона не расходится");
    }

    @Test
    void assist_AttackedUnknown_ReturnsEmpty() {
        SpiritSnapshot near = defender(NEAR, 2, 0, false);
        UUID unknown = UUID.fromString("44444444-4444-4444-4444-444444444444");

        assertTrue(new AssistPolicy().assist(unknown, List.of(near), List.of(post(NEAR, 2)), 2).isEmpty(),
                "Атакованный вне снимка неизвестен: помощь не выбирается без его позиции");
    }

    @Test
    void assist_DefenderWithoutPost_OnDutyChosen() {
        SpiritSnapshot ward = worker(WARD, 0);
        SpiritSnapshot offDuty = defender(NEAR, 1, 0, false);
        SpiritSnapshot onDuty = defender(FAR, 6, 0, false);

        List<UUID> helpers = new AssistPolicy().assist(WARD, List.of(ward, offDuty, onDuty), List.of(post(FAR, 6)), 2);

        assertEquals(List.of(FAR), helpers,
                "Защитник без поста не в обороне: помощь идёт от тех, кто держит сектор");
    }

    @Test
    void assist_NullAttacked_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new AssistPolicy().assist(null, List.of(), List.of(), 2),
                "Помощь неизвестному духу не нужна: атакованный обязателен");
    }

    @Test
    void assist_NullSpirits_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new AssistPolicy().assist(WARD, null, List.of(), 2),
                "null вместо снимков не говорит, есть ли духи: передавайте пустой список");
    }

    @Test
    void assist_NullPosts_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new AssistPolicy().assist(WARD, List.of(), null, 2),
                "null вместо постов не говорит, есть ли оборона: передавайте пустой список");
    }

    @Test
    void assist_NegativeMaxHelpers_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new AssistPolicy().assist(WARD, List.of(), List.of(), -1),
                "Отрицательный предел помощников не имеет смысла");
    }
}
