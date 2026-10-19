package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class PolicyVerdictTests {
    private static final BlockCoord CELL = new BlockCoord(1, 2, 3);

    @Test
    void reflex_FleeWithCell_KeepsPolicyActionAndReason() {
        PolicyVerdict.Reflex reflex =
                new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, new ReflexAction.Flee(CELL), "в лаве");

        assertEquals(PolicyId.LAVA_FIRE, reflex.policy(), "политика вердикта нужна исполнителю для лога и исключений");
        assertEquals(new ReflexAction.Flee(CELL), reflex.action(), "действие несёт клетку, куда вести духа");
        assertEquals("в лаве", reflex.reason(), "причина попадает в лог и итог задачи");
    }

    @Test
    void reflexAction_CellActions_ExposeTheirCell() {
        assertEquals(CELL, new ReflexAction.ExitWater(CELL).cell(), "EXIT_WATER ведёт духа в клетку выхода");
        assertEquals(
                CELL,
                new ReflexAction.StepOutOfPortal(CELL).cell(),
                "STEP_OUT_OF_PORTAL ведёт духа в клетку вне портала");
        assertEquals(CELL, new ReflexAction.ReturnToZone(CELL).cell(), "RETURN_TO_ZONE ведёт духа в точку внутри зоны");
    }

    @Test
    void reflexAction_CellActionWithoutCell_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class, () -> new ReflexAction.Flee(null), "FLEE без клетки не к чему исполнять");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.ExitWater(null),
                "EXIT_WATER без клетки не к чему исполнять");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.StepOutOfPortal(null),
                "STEP_OUT_OF_PORTAL без клетки не к чему исполнять");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.ReturnToZone(null),
                "RETURN_TO_ZONE без клетки не к чему исполнять");
    }

    @Test
    void reflex_NullPolicy_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(null, new ReflexAction.Surface(), "воздух"),
                "рефлекс без политики нельзя сопоставить с исключениями");
    }

    @Test
    void reflex_NullAction_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, null, "воздух"),
                "рефлекс без действия оставил бы духа без команды");
    }

    @Test
    void reflex_NullReason_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, new ReflexAction.Surface(), null),
                "рефлекс без причины нельзя разобрать по логу");
    }

    @Test
    void reflex_BlankReason_ThrowsIllegalArgument() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, new ReflexAction.Surface(), "  "),
                "пустая причина — тот же дефект, что и null");

        assertEquals(
                "Причина рефлекса пуста: лог «дух побежал» без причины нельзя разобрать",
                error.getMessage(),
                "ошибка называет, что пусто и почему это плохо");
    }

    @Test
    void clear_TwoInstances_AreEqual() {
        assertEquals(
                new PolicyVerdict.Clear(),
                new PolicyVerdict.Clear(),
                "Clear не несёт данных: все экземпляры равны, сравнение вердиктов в тестах опирается на это");
    }
}
