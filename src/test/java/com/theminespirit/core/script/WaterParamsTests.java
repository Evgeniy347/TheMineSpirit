package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WaterParamsTests {
    @Test
    void swim_Coordinates_AreRead() {
        SwimParams params = SwimParams.from(Map.of("x", 10, "y", 63, "z", -4));

        assertEquals(10, params.x(), "x цели заплыва обязан читаться из плана: без него дух плывёт не туда");
        assertEquals(63, params.y(), "y цели заплыва обязан читаться из плана: иначе дух вынырнет не на том уровне");
        assertEquals(-4, params.z(), "z цели заплыва обязан читаться из плана: иначе дух плывёт не туда");
    }

    @Test
    void swim_MissingZ_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SwimParams.from(Map.of("x", 1, "y", 63)),
                "Без z заплыв не задан: молчаливое умолчание увело бы духа по чужой координате");
    }

    @Test
    void crossWater_PointOnly_ParsesCoordinates() {
        CrossWaterParams params = CrossWaterParams.from(Map.of("x", 1, "y", 63, "z", 2));

        assertEquals(
                new CrossWaterParams(1, 63, 2),
                params,
                "Переправа — только точка: мост в переправе убран (упрощение 44.7), материала она не берёт");
    }

    @Test
    void bridge_LengthGivenBlockOmitted_BlockIsNull() {
        BridgeParams params = BridgeParams.from(Map.of("x", 1, "y", 63, "z", 2, "length", 7));

        assertEquals(7, params.length(), "Длина моста обязана читаться из плана: без неё мост не задать");
        assertNull(params.block(), "Без block материал моста выбирается из инвентаря, а не из плана");
    }

    @Test
    void bridge_LengthBelowMinimum_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeParams.from(Map.of("x", 1, "y", 63, "z", 2, "length", 0)),
                "Нулевая длина моста бессмысленна: мост не из чего строить, план обязан отклоняться");
    }

    @Test
    void bridge_LengthAboveMaximum_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeParams.from(Map.of("x", 1, "y", 63, "z", 2, "length", CatalogLimits.BRIDGE_LENGTH_MAX + 1)),
                "Длина моста сверх предела обязана отклоняться: бесконечный мост съел бы инвентарь и время");
    }

    @Test
    void bridge_LengthAtMaximum_IsAccepted() {
        BridgeParams params =
                BridgeParams.from(Map.of("x", 1, "y", 63, "z", 2, "length", CatalogLimits.BRIDGE_LENGTH_MAX));

        assertEquals(
                CatalogLimits.BRIDGE_LENGTH_MAX,
                params.length(),
                "Длина ровно на верхней границе обязана приниматься: граница включена в допустимый мост");
    }

    @Test
    void bridge_XOutOfWorldRange_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeParams.from(
                        Map.of("x", CatalogLimits.WORLD_HORIZONTAL_LIMIT + 1, "y", 63, "z", 2, "length", 4)),
                "Точка моста вне мира обязана отклоняться: там нечего строить");
    }
}
