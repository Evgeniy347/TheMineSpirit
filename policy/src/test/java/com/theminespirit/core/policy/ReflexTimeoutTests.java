package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReflexTimeoutTests {
    private static final int LIMIT = 100;
    private static final String AIR_KEY = "AIR:Surface";
    private static final String WATER_KEY = "WATER:ExitWater";

    @Test
    void expired_AirUnderWaterForLongerThanLimit_KeepsReflex() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < 500; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.AIR, AIR_KEY, LIMIT, true, false),
                    "Рефлекс AIR:Surface под водой нельзя глушить на тике " + tick + ": предел " + LIMIT
                            + " тиков сработал в наблюдении 30.10, дух перестал всплывать и утонул в (-264,54,-490)"
                            + " с запасом воздуха в минусе");
        }
    }

    @Test
    void expired_WaterUnderWaterForLongerThanLimit_KeepsReflex() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < 500; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.WATER, WATER_KEY, LIMIT, true, false),
                    "Рефлекс выхода из воды нельзя глушить на тике " + tick + ", пока голова под водой: " + "после "
                            + LIMIT + " тиков дух останавливается и тонет");
        }
    }

    @Test
    void expired_WaterInTrapWithoutProgress_StopsAfterLimit() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < LIMIT - 1; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.WATER, WATER_KEY, LIMIT, false, false),
                    "Пока предел " + LIMIT + " тиков не исчерпан, рефлекс выхода из воды законен: тик " + tick);
        }
        assertTrue(
                timeout.expired(PolicyId.WATER, WATER_KEY, LIMIT, false, false),
                "Дух в воде с головой на воздухе, но клетка ног не меняется — он заперт: рефлекс обязан "
                        + "уступить работу спасению (10.11, шестеро стояли в -99,52,-417 больше трёх часов)");
    }

    @Test
    void expired_WaterAtSurfaceMakingProgress_KeepsReflex() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < 500; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.WATER, WATER_KEY, LIMIT, false, true),
                    "Пока дух плывёт (клетка ног сменилась), рефлекс выхода из воды держится: иначе он "
                            + "останавливается посреди озера и снова уплывает по маршруту (observe17 A2/N5)");
        }
    }

    @Test
    void expired_LavaReflexAfterLimit_Stops() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < LIMIT - 1; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.LAVA_FIRE, "LAVA_FIRE:Flee", LIMIT, false, false),
                    "Лава-рефлекс ещё не исчерпал " + LIMIT + " тиков: остановка на тике " + tick + " преждевременна");
        }
        assertTrue(
                timeout.expired(PolicyId.LAVA_FIRE, "LAVA_FIRE:Flee", LIMIT, true, true),
                "Предел глушит любой рефлекс, кроме воды и воздуха: даже флаги «под водой» и «движется» не "
                        + "спасают духа от бесконечного бегства из лавы");
    }

    @Test
    void expired_DifferentKey_CountsFromOne() {
        ReflexTimeout timeout = new ReflexTimeout();
        for (int tick = 0; tick < LIMIT - 1; tick++) {
            timeout.expired(PolicyId.DROP, "DROP:Flee", LIMIT, false, false);
        }

        assertFalse(
                timeout.expired(PolicyId.DROP, "DROP:ReturnToZone", LIMIT, false, false),
                "Смена ключа рефлекса начинает отсчёт заново: иначе новый рефлекс глушится чужими тиками");
    }

    @Test
    void expired_BlankKey_IsRejected() {
        ReflexTimeout timeout = new ReflexTimeout();

        assertThrows(
                IllegalArgumentException.class,
                () -> timeout.expired(PolicyId.AIR, " ", LIMIT, false, false),
                "Пустой ключ рефлекса скрывает, какой рефлекс крутится: строка «политика:действие» обязательна");
    }
}
