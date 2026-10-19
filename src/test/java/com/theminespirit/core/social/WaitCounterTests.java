package com.theminespirit.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitCounterTests {
    @Test
    void tick_TargetTicksElapsed_ReportsDoneOnLastTick() {
        WaitCounter counter = WaitCounter.start(3);

        assertFalse(counter.tick(), "Первый тик обязан ждать: осталось ещё два, ранний выход оборвал бы ожидание");
        assertFalse(counter.tick(), "Второй тик обязан ждать: остался ещё один, ранний выход оборвал бы ожидание");
        assertTrue(counter.tick(), "Третий тик обязан закрыть ожидание: счётчик отсчитал ровно заданные тики");
    }

    @Test
    void remaining_FreshCounter_EqualsTargetTicks() {
        assertEquals(
                5,
                WaitCounter.start(5).remaining(),
                "Свежий счётчик обязан показывать весь срок: до первого тика ожидание не начиналось");
    }

    @Test
    void remaining_EachTickPassed_DecreasesByOne() {
        WaitCounter counter = WaitCounter.start(4);

        assertFalse(
                counter.tick(),
                "Первый тик из четырёх обязан ждать: осталось ещё три, ранний выход оборвал бы ожидание");
        assertEquals(
                3,
                counter.remaining(),
                "После первого тика остаток обязан упасть до 3: счётчик обязан отсчитывать тики, а не топтаться");
        counter.tick();
        assertEquals(
                2, counter.remaining(), "После второго тика остаток обязан стать 2: каждый тик съедает ровно один");
        counter.tick();
        assertEquals(1, counter.remaining(), "После третьего тика остаток обязан стать 1: до конца ожидания один тик");
    }

    @Test
    void toMap_RoundTrip_PreservesCounter() {
        WaitCounter counter = WaitCounter.start(6);
        counter.tick();
        counter.tick();

        WaitCounter restored = WaitCounter.fromMap(counter.toMap());

        assertEquals(
                counter.remaining(),
                restored.remaining(),
                "Снимок обязан возвращать тот же остаток: иначе после перезагрузки дух ждал бы не то время");
        assertEquals(
                counter.elapsed(),
                restored.elapsed(),
                "Снимок обязан возвращать тот же счётчик прошедших тиков: иначе ожидание началось бы заново");
        assertFalse(
                restored.tick(),
                "Восстановленный счётчик обязан ждать ровно столько же: "
                        + "первый тик после восстановления не должен закрывать ожидание раньше срока");
    }

    @Test
    void fromMap_MissingKeys_ReturnsZeroRemaining() {
        WaitCounter restored = WaitCounter.fromMap(Map.of());

        assertEquals(
                0,
                restored.remaining(),
                "Снимок без ключей обязан давать нулевой остаток: старый снимок не знает, сколько ждали, "
                        + "а зависшее ожидание без конца хуже, чем немедленное завершение");
        assertTrue(
                restored.tick(),
                "Счётчик из снимка без ключей обязан завершиться на первом тике: задача не имеет права "
                        + "зависнуть в вечном ожидании");
    }

    @Test
    void start_NegativeTicks_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.start(-1),
                "Отрицательный срок ожидания обязан отказывать: счётчик завершился бы раньше, чем начался");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть отклонённое число тиков. Фактическое: " + error.getMessage());
    }

    @Test
    void fromMap_NullState_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> WaitCounter.fromMap(null),
                "WaitCounter.fromMap(null) обязан отказывать: снимок обязан прийти картой, "
                        + "а null означает повреждение состояния задачи");
    }

    @Test
    void fromMap_NonNumericValue_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.fromMap(Map.of(WaitCounter.TARGET_KEY, "5")),
                "Нечисловое значение счётчика обязано отказывать: молчаливая подмена скрыла бы повреждённый снимок");

        assertTrue(
                error.getMessage().contains(WaitCounter.TARGET_KEY),
                "Отказ обязан называть повреждённый ключ. Фактическое: " + error.getMessage());
    }

    @Test
    void fromMap_NegativeElapsed_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.fromMap(Map.of(WaitCounter.TARGET_KEY, 5, WaitCounter.ELAPSED_KEY, -1)),
                "Отрицательный счётчик прошедшего времени обязан отказывать: снимок повреждён");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть отклонённое число. Фактическое: " + error.getMessage());
    }
}
