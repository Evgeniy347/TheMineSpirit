package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.api.PolicyId;
import org.junit.jupiter.api.Test;

class PolicyVerdictTests {
    private static final BlockCoord CELL = new BlockCoord(1, 2, 3);

    @Test
    void reflex_FleeWithCell_KeepsPolicyActionAndReason() {
        PolicyVerdict.Reflex reflex =
                new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, new ReflexAction.Flee(CELL), "в лаве");

        assertEquals(
                PolicyId.LAVA_FIRE,
                reflex.policy(),
                "политика вердикта нужна исполнителю для лога и исключений, иначе рефлекс не сопоставить с настройками "
                        + "и отключить нельзя. При смене конструктора проверь сохранение PolicyId");
        assertEquals(
                new ReflexAction.Flee(CELL),
                reflex.action(),
                "действие несёт клетку, куда вести духа, иначе бегство ведёт в никуда и дух теряет тик. "
                        + "При смене Reflex проверь эквивалентность действия по клетке и типу");
        assertEquals(
                "в лаве",
                reflex.reason(),
                "причина попадает в лог и итог задачи, иначе по записи не понять, почему дух сорвался, "
                        + "и инцидент не разобрать. При смене конструктора проверь дословный текст причины");
    }

    @Test
    void reflexAction_CellActions_ExposeTheirCell() {
        assertEquals(
                CELL,
                new ReflexAction.ExitWater(CELL).cell(),
                "EXIT_WATER ведёт духа в клетку выхода, иначе он всплывает на месте и теряет воздух. "
                        + "При смене ExitWater проверь сохранение клетки выхода");
        assertEquals(
                CELL,
                new ReflexAction.StepOutOfPortal(CELL).cell(),
                "STEP_OUT_OF_PORTAL ведёт духа в клетку вне портала, иначе шаг остаётся в портале и переносит "
                        + "духа в другое измерение. При смене действия проверь сохранение клетки шага");
        assertEquals(
                CELL,
                new ReflexAction.ReturnToZone(CELL).cell(),
                "RETURN_TO_ZONE ведёт духа в точку внутри зоны, иначе возврат идёт не туда и работа стоит. "
                        + "При смене действия проверь сохранение клетки возврата");
    }

    @Test
    void reflexAction_CellActionWithoutCell_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.Flee(null),
                "FLEE без клетки не к чему исполнять: null обязан падать сразу, иначе бегство потеряет цель. При смене конструктора проверь null");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.ExitWater(null),
                "EXIT_WATER без клетки не к чему исполнять: иначе всплытие теряет точку и дух застрянет. При смене конструктора проверь null");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.StepOutOfPortal(null),
                "STEP_OUT_OF_PORTAL без клетки не к чему исполнять: иначе шаг теряет точку и дух застрянет. При смене конструктора проверь null");
        assertThrows(
                NullPointerException.class,
                () -> new ReflexAction.ReturnToZone(null),
                "RETURN_TO_ZONE без клетки не к чему исполнять: иначе возврат теряет точку и дух останется вне зоны. При смене конструктора проверь null");
    }

    @Test
    void reflex_NullPolicy_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(null, new ReflexAction.Surface(), "воздух"),
                "рефлекс без политики нельзя сопоставить с исключениями, иначе отключить его нельзя. "
                        + "При смене конструктора проверь null-политику");
    }

    @Test
    void reflex_NullAction_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, null, "воздух"),
                "рефлекс без действия оставил бы духа без команды, иначе владение тиком уйдёт вхолостую. "
                        + "При смене конструктора проверь null-действие");
    }

    @Test
    void reflex_NullReason_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, new ReflexAction.Surface(), null),
                "рефлекс без причины нельзя разобрать по логу, иначе инцидент останется без объяснения. "
                        + "При смене конструктора проверь null-причину");
    }

    @Test
    void reflex_BlankReason_ThrowsIllegalArgument() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyVerdict.Reflex(PolicyId.AIR, new ReflexAction.Surface(), "  "),
                "пустая причина — тот же дефект, что и null, иначе лог получает пробел вместо объяснения. "
                        + "При смене проверки причины сверь строку из одних пробелов");

        assertEquals(
                "Причина рефлекса пуста: лог «дух побежал» без причины нельзя разобрать",
                error.getMessage(),
                "ошибка называет, что пусто и почему это плохо, иначе игрок не поймёт, что исправлять. "
                        + "При смене текста проверь, что в нём остаются оба утверждения");
    }

    @Test
    void clear_TwoInstances_AreEqual() {
        assertEquals(
                new PolicyVerdict.Clear(),
                new PolicyVerdict.Clear(),
                "Clear не несёт данных: все экземпляры равны, иначе сравнение вердиктов в тестах сломается. "
                        + "При смене Clear проверь, что equals не зависит от идентичности объекта");
    }
}
