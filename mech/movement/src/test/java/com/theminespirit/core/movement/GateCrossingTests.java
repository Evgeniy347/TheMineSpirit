package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateCrossingTests {
    private static final BlockCoord GATE = new BlockCoord(-170, 70, -498);
    private static final BlockCoord NEAR = new BlockCoord(-170, 70, -501);
    private static final BlockCoord BESIDE = new BlockCoord(-170, 70, -499);
    private static final BlockCoord BEHIND = new BlockCoord(-170, 70, -497);

    @Test
    void observe_OpenThenGateCellThenBehind_ClosableOnlyBehind() {
        GateCrossing crossing = new GateCrossing(GATE, NEAR);
        List<GateCrossing.Phase> phases =
                List.of(crossing.observe(NEAR), crossing.observe(BESIDE), crossing.observe(GATE));

        assertEquals(
                List.of(GateCrossing.Phase.OPENED, GateCrossing.Phase.OPENED, GateCrossing.Phase.INSIDE),
                phases,
                "До клетки створки дух ещё не прошёл (INC-82: калитку закрывали через 50 мс после открытия, дух "
                        + "упирался в неё); проверь условие «в клетке створки»"
                        + " Иначе тест observe_OpenThenGateCellThenBehind_ClosableOnlyBehind перестанет ловить эту поломку. При смене observe_OpenThenGateCellThenBehind_ClosableOnlyBehind проверь это утверждение.");
        assertFalse(
                crossing.closable(),
                "Дух стоит в клетке калитки: закрыть сейчас — запереть его в створке"
                        + " Иначе тест observe_OpenThenGateCellThenBehind_ClosableOnlyBehind перестанет ловить эту поломку. При смене observe_OpenThenGateCellThenBehind_ClosableOnlyBehind проверь это утверждение.");
        assertEquals(
                GateCrossing.Phase.BEYOND,
                crossing.observe(BEHIND),
                "Шаг за створку на другую сторону — проход завершён"
                        + " Иначе тест observe_OpenThenGateCellThenBehind_ClosableOnlyBehind перестанет ловить эту поломку. При смене observe_OpenThenGateCellThenBehind_ClosableOnlyBehind проверь это утверждение.");
        assertTrue(
                crossing.closable(),
                "Дух за калиткой: теперь створку закрывают, иначе ограда открыта для мобов"
                        + " При смене observe_OpenThenGateCellThenBehind_ClosableOnlyBehind проверь это утверждение.");
    }

    @Test
    void observe_StepsBackToOpeningSide_NotClosable() {
        GateCrossing crossing = new GateCrossing(GATE, NEAR);
        crossing.observe(GATE);

        assertEquals(
                GateCrossing.Phase.OPENED,
                crossing.observe(BESIDE),
                "Дух вернулся на сторону, откуда открыл калитку: он не прошёл, закрывать рано"
                        + " Иначе тест observe_StepsBackToOpeningSide_NotClosable перестанет ловить эту поломку. При смене observe_StepsBackToOpeningSide_NotClosable проверь это утверждение.");
        assertFalse(
                crossing.closable(),
                "Без прохода на другую сторону калитку не закрывают: дух пойдёт снова"
                        + " Иначе тест observe_StepsBackToOpeningSide_NotClosable перестанет ловить эту поломку. При смене observe_StepsBackToOpeningSide_NotClosable проверь это утверждение.");
    }

    @Test
    void observe_NeverEnteredButOtherSide_NotClosable() {
        GateCrossing crossing = new GateCrossing(GATE, NEAR);

        crossing.observe(BEHIND);

        assertFalse(
                crossing.closable(),
                "Без клетки створки проход не засчитан: последовательность — открыть, клетка калитки, за калиткой"
                        + " Иначе тест observe_NeverEnteredButOtherSide_NotClosable перестанет ловить эту поломку. При смене observe_NeverEnteredButOtherSide_NotClosable проверь это утверждение.");
    }
}
