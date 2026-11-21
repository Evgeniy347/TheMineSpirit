package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavReversibilityTests {
    @Test
    void pit_TenDeep_NoWalkingReturn() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int y = 0; y >= -8; y--) {
            world.air(3, y, 0).air(4, y, 0);
        }

        assertFalse(
                NavReversibility.canReturn(world, 3, -8, 0, 1),
                "Со дна ямы 10 блоков не выйти шагом: спуск туда необратим, навигация обязана его запретить "
                        + "(observe24 B1)"
                        + " Иначе тест pit_TenDeep_NoWalkingReturn перестанет ловить эту поломку. При смене pit_TenDeep_NoWalkingReturn проверь это утверждение.");
    }

    @Test
    void pit_OneStepDown_ReturnAllowed() {
        NavTestWorld world = NavTestWorld.flat(0).air(3, 0, 0).air(4, 0, 0);

        assertTrue(
                NavReversibility.canReturn(world, 3, 0, 0, 1),
                "Ступенька вниз на блок обратима: обычный рельеф навигация не запрещает"
                        + " Иначе тест pit_OneStepDown_ReturnAllowed перестанет ловить эту поломку. При смене pit_OneStepDown_ReturnAllowed проверь это утверждение.");
    }

    @Test
    void cave_WaterPoolWithoutShores_NoReturn() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int x = 2; x <= 6; x++) {
            for (int z = -1; z <= 1; z++) {
                world.air(x, -1, z);
            }
        }
        for (int x = 4; x <= 6; x++) {
            world.wet(x, -1, 0);
        }
        world.air(2, 0, 0);

        assertFalse(
                NavReversibility.canReturn(world, 5, -1, 0, 1),
                "Вода в пещере без выхода не считается путём наверх: из пула нет шага на поверхность"
                        + " Иначе тест cave_WaterPoolWithoutShores_NoReturn перестанет ловить эту поломку. При смене cave_WaterPoolWithoutShores_NoReturn проверь это утверждение.");
    }

    @Test
    void stairs_BackUp_ReturnAllowed() {
        NavTestWorld world = NavTestWorld.flat(0).air(2, 0, 0).air(3, 0, 0).air(3, -1, 0);

        assertTrue(
                NavReversibility.canReturn(world, 3, -1, 0, 1),
                "Спуск по ступеням обратим: из ямы со ступенями выход есть"
                        + " Иначе тест stairs_BackUp_ReturnAllowed перестанет ловить эту поломку. При смене stairs_BackUp_ReturnAllowed проверь это утверждение.");
    }
}
