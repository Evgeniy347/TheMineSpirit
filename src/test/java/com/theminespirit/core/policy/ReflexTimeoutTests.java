package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReflexTimeoutTests {
    private static final int LIMIT = 100;
    private static final String AIR_KEY = "AIR:Surface";

    @Test
    void expired_AirUnderWaterForLongerThanLimit_KeepsReflex() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < 500; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.AIR, AIR_KEY, LIMIT, true),
                    "Рефлекс AIR:Surface под водой нельзя глушить на тике " + tick + ": предел " + LIMIT
                            + " тиков сработал в наблюдении 30.10, дух перестал всплывать и утонул в (-264,54,-490)"
                            + " с запасом воздуха в минусе");
        }
    }

    @Test
    void expired_LavaReflexAfterLimit_Stops() {
        ReflexTimeout timeout = new ReflexTimeout();

        for (int tick = 0; tick < LIMIT - 1; tick++) {
            assertFalse(
                    timeout.expired(PolicyId.LAVA_FIRE, "LAVA_FIRE:Flee", LIMIT, false),
                    "Лава-рефлекс ещё не исчерпал " + LIMIT + " тиков: остановка на тике " + tick + " преждевременна");
        }
        assertTrue(
                timeout.expired(PolicyId.LAVA_FIRE, "LAVA_FIRE:Flee", LIMIT, true),
                "Предел глушит любой рефлекс, кроме всплытия под водой: даже флаг «голова под водой» не спасает "
                        + "духа от бесконечного бегства из лавы");
    }

    @Test
    void expired_DifferentKey_CountsFromOne() {
        ReflexTimeout timeout = new ReflexTimeout();
        for (int tick = 0; tick < LIMIT - 1; tick++) {
            timeout.expired(PolicyId.DROP, "DROP:Flee", LIMIT, false);
        }

        assertFalse(
                timeout.expired(PolicyId.DROP, "DROP:ReturnToZone", LIMIT, false),
                "Смена ключа рефлекса начинает отсчёт заново: иначе новый рефлекс глушится чужими тиками");
    }

    @Test
    void expired_BlankKey_IsRejected() {
        ReflexTimeout timeout = new ReflexTimeout();

        assertThrows(
                IllegalArgumentException.class,
                () -> timeout.expired(PolicyId.AIR, " ", LIMIT, false),
                "Пустой ключ рефлекса скрывает, какой рефлекс крутится: строка «политика:действие» обязательна");
    }
}
