package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SpiritReachTests {
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);
    private static final SpiritReach OWNER = new SpiritReach(3, 15, 3);

    @Test
    void table_OwnerDecision_HorizontalThreeUpFifteenDownThreeAndTwoBlocksPerSecond() {
        BuildData data = BuildData.load();

        assertEquals(
                OWNER,
                data.reach(),
                "Досягаемость задана владельцем (решение 18.10): 3 по горизонтали, 15 вверх, 3 вниз; "
                        + "менять — в building/build.json и вместе с правилом досягаемости");
        assertEquals(
                10,
                data.placeIntervalTicks(),
                "Темп постановки — примерно 2 блока в секунду (решение владельца 18.10): 10 тиков между блоками");
        assertEquals(
                2,
                data.intervalTicks(true),
                "Темп демо-стройки — 10 блоков в секунду (решение владельца 19.10): 2 тика между блоками; темп "
                        + "обычной игры при этом не меняется");
        assertEquals(10, data.intervalTicks(false), "Обычная стройка обязана идти темпом placeIntervalTicks");
    }

    @Test
    void reaches_ThreeAcrossFifteenUp_IsReachable() {
        assertTrue(
                OWNER.reaches(FEET, new BlockCoord(13, 79, 10)),
                "Клетка в 3 по горизонтали и 15 вверх обязана быть досягаемой: иначе высокая стена строится лесами");
    }

    @Test
    void reaches_FourAcross_IsNotReachable() {
        assertFalse(
                OWNER.reaches(FEET, new BlockCoord(14, 64, 10)),
                "Клетка в 4 по горизонтали обязана быть недосягаемой: иначе правило 3 блоков не работает");
    }

    @Test
    void forbiddenStand_TargetAndCellBelow_AreForbidden() {
        BlockCoord target = new BlockCoord(10, 64, 10);
        assertTrue(
                SpiritReach.forbiddenStand(target, target),
                "Стоянка в целевой клетке запрещена: дух не может ставить блок в себя");
        assertTrue(
                SpiritReach.forbiddenStand(new BlockCoord(10, 63, 10), target),
                "Стоянка в клетке под целью запрещена: дух стоит там, куда падает блок");
        assertFalse(
                SpiritReach.forbiddenStand(new BlockCoord(11, 64, 10), target),
                "Соседняя клетка — законная стоянка: без неё дух не подойдёт к стене");
    }

    @Test
    void reaches_ThreeDown_IsReachableAndFourDownIsNot() {
        assertTrue(
                OWNER.reaches(FEET, new BlockCoord(10, 61, 10)),
                "Клетка в 3 вниз обязана быть досягаемой: дух ставит фундамент под собой");
        assertFalse(
                OWNER.reaches(FEET, new BlockCoord(10, 60, 10)),
                "Клетка в 4 вниз обязана быть недосягаемой: иначе дух ставит сквозь пол");
    }

    @Test
    void reaches_TableNumbers_AreUsed() {
        SpiritReach wide = new SpiritReach(5, 2, 1);

        assertTrue(
                wide.reaches(FEET, new BlockCoord(15, 66, 10)),
                "Досягаемость обязана браться из чисел таблицы, а не из констант: 5 по горизонтали и 2 вверх");
        assertFalse(wide.reaches(FEET, new BlockCoord(10, 67, 10)), "3 вверх при пределе 2 — вне досягаемости");
    }

    @Test
    void parse_ZeroInterval_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BuildData.parse(
                        "{\"reachHorizontal\":3,\"reachUp\":15,\"reachDown\":3,\"placeIntervalTicks\":0,\"demoPlaceIntervalTicks\":2}",
                        "тест"),
                "Нулевой интервал постановки обязан отвергаться: дух ставил бы весь дом за тик");

        assertTrue(error.getMessage().contains("placeIntervalTicks"), "Ошибка называет поле: " + error.getMessage());
    }
}
