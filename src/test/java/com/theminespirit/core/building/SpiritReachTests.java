package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SpiritReachTests {
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);

    @Test
    void constants_OwnerDecision_HorizontalThreeUpFifteenDownThree() {
        assertEquals(3, SpiritReach.HORIZONTAL,
                "Предел по горизонтали задан владельцем (решение 18.10): 3 блока; "
                        + "менять только вместе с правилом досягаемости");
        assertEquals(15, SpiritReach.UP,
                "Предел вверх задан владельцем (решение 18.10): 15 блоков от ног; "
                        + "менять только вместе с правилом досягаемости");
        assertEquals(3, SpiritReach.DOWN,
                "Предел вниз — разумное значение владельца: дух ставит блоки и чуть ниже ног");
    }

    @Test
    void reachable_ThreeAcrossFifteenUp_IsReachable() {
        assertTrue(SpiritReach.reachable(FEET, new BlockCoord(13, 79, 10)),
                "Клетка в 3 по горизонтали и 15 вверх обязана быть досягаемой: иначе высокая стена строится лесами");
    }

    @Test
    void reachable_FourAcross_IsNotReachable() {
        assertFalse(SpiritReach.reachable(FEET, new BlockCoord(14, 64, 10)),
                "Клетка в 4 по горизонтали обязана быть недосягаемой: иначе правило 3 блоков не работает");
    }

    @Test
    void forbiddenStand_TargetAndCellBelow_AreForbidden() {
        BlockCoord target = new BlockCoord(10, 64, 10);
        assertTrue(SpiritReach.forbiddenStand(target, target),
                "Стоянка в целевой клетке запрещена: дух не может ставить блок в себя");
        assertTrue(SpiritReach.forbiddenStand(new BlockCoord(10, 63, 10), target),
                "Стоянка в клетке под целью запрещена: дух стоит там, куда падает блок");
        assertFalse(SpiritReach.forbiddenStand(new BlockCoord(11, 64, 10), target),
                "Соседняя клетка — законная стоянка: без неё дух не подойдёт к стене");
    }

    @Test
    void reachable_ThreeDown_IsReachableAndFourDownIsNot() {
        assertTrue(SpiritReach.reachable(FEET, new BlockCoord(10, 61, 10)),
                "Клетка в 3 вниз обязана быть досягаемой: дух ставит фундамент под собой");
        assertFalse(SpiritReach.reachable(FEET, new BlockCoord(10, 60, 10)),
                "Клетка в 4 вниз обязана быть недосягаемой: иначе дух ставит сквозь пол");
    }
}
