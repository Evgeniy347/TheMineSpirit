package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitParamsTests {
    @Test
    void from_TicksInsideRange_KeepsTicks() {
        WaitParams params = WaitParams.from(Map.of("ticks", 40));

        assertEquals(
                40,
                params.ticks(),
                "wait обязан сохранить число тиков: по нему счётчик отсчитывает ожидание"
                        + " Иначе тест from_TicksInsideRange_KeepsTicks перестанет ловить эту поломку. При смене from_TicksInsideRange_KeepsTicks проверь это утверждение.");
    }

    @Test
    void from_TicksAtMinimum_IsAccepted() {
        assertEquals(
                1,
                WaitParams.from(Map.of("ticks", 1)).ticks(),
                "wait на нижней границе обязан приниматься: граница 1 тик допустима, иначе мгновенное ожидание невозможно. "
                        + "Граница в каталоге: " + CatalogLimits.WAIT_TICKS_MIN
                        + " При смене from_TicksAtMinimum_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_TicksAtMaximum_IsAccepted() {
        assertEquals(
                6000,
                WaitParams.from(Map.of("ticks", 6000)).ticks(),
                "wait на верхней границе обязан приниматься: граница 6000 тиков (5 минут) допустима. "
                        + "Граница в каталоге: " + CatalogLimits.WAIT_TICKS_MAX
                        + " Иначе тест from_TicksAtMaximum_IsAccepted перестанет ловить эту поломку. При смене from_TicksAtMaximum_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_TicksBelowRange_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitParams.from(Map.of("ticks", 0)),
                "wait меньше нижней границы обязан отказывать: 0 тиков — не ожидание, а мгновенный выход"
                        + " Иначе тест from_TicksBelowRange_IsRejected перестанет ловить эту поломку. При смене from_TicksBelowRange_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.WAIT_TICKS_MIN)),
                "Отказ обязан называть нижнюю границу. Фактическое: " + error.getMessage()
                        + " Иначе тест from_TicksBelowRange_IsRejected перестанет ловить эту поломку. При смене from_TicksBelowRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_TicksAboveRange_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitParams.from(Map.of("ticks", CatalogLimits.WAIT_TICKS_MAX + 1)),
                "wait больше верхней границы обязан отказывать: лимит держит задачу живой не дольше 5 минут, "
                        + "иначе стратег ждёт висячий план"
                        + " При смене from_TicksAboveRange_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.WAIT_TICKS_MAX)),
                "Отказ обязан называть верхнюю границу. Фактическое: " + error.getMessage()
                        + " Иначе тест from_TicksAboveRange_IsRejected перестанет ловить эту поломку. При смене from_TicksAboveRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingTicks_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitParams.from(Map.of()),
                "wait без ticks обязан отказывать: без числа счётчик не знает, сколько ждать"
                        + " Иначе тест from_MissingTicks_IsRejected перестанет ловить эту поломку. При смене from_MissingTicks_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("ticks"),
                "Отказ обязан называть недостающий параметр 'ticks'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingTicks_IsRejected перестанет ловить эту поломку. При смене from_MissingTicks_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NonIntegerTicks_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WaitParams.from(Map.of("ticks", 40L)),
                "wait с нецелым значением обязан отказывать: long не прошёл валидатор плана"
                        + " Иначе тест from_NonIntegerTicks_IsRejected перестанет ловить эту поломку. При смене from_NonIntegerTicks_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> WaitParams.from(null),
                "WaitParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
