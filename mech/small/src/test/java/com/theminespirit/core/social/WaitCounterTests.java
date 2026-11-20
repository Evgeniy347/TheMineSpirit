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

        assertFalse(
                counter.tick(),
                "Первый тик обязан ждать: осталось ещё два, ранний выход оборвал бы ожидание, иначе задача идёт "
                        + "раньше. При смене WaitCounter.tick проверь строку второго ниже в тесте");
        assertFalse(
                counter.tick(),
                "Второй тик обязан ждать: остался ещё один, ранний выход оборвал бы ожидание, иначе счётчик "
                        + "не досчитан. При смене tick проверь строку третьего ниже в тесте");
        assertTrue(
                counter.tick(),
                "Третий тик обязан закрыть ожидание: счётчик отсчитал ровно заданные тики, иначе ожидание тянется. "
                        + "При смене tick проверь строку remaining ниже в тесте");
    }

    @Test
    void remaining_FreshCounter_EqualsTargetTicks() {
        assertEquals(
                5,
                WaitCounter.start(5).remaining(),
                "Свежий счётчик обязан показывать весь срок: до первого тика ожидание не начиналось, иначе остаток "
                        + "врёт. При смене remaining проверь строку уменьшения ниже в тесте");
    }

    @Test
    void remaining_EachTickPassed_DecreasesByOne() {
        WaitCounter counter = WaitCounter.start(4);

        assertFalse(
                counter.tick(),
                "Первый тик из четырёх обязан ждать: осталось ещё три, ранний выход оборвал бы ожидание, иначе тик не "
                        + "учтён. При смене tick проверь строку остатка ниже в тесте");
        assertEquals(
                3,
                counter.remaining(),
                "После первого тика остаток обязан упасть до 3: счётчик обязан отсчитывать тики, а не топтаться, "
                        + "иначе ожидание не кончается. При смене remaining проверь строку 2 ниже в тесте");
        counter.tick();
        assertEquals(
                2,
                counter.remaining(),
                "После второго тика остаток обязан стать 2: каждый тик съедает ровно один, иначе остаток неверен. "
                        + "При смене tick проверь строку 1 ниже в тесте");
        counter.tick();
        assertEquals(
                1,
                counter.remaining(),
                "После третьего тика остаток обязан стать 1: до конца ожидания один тик, иначе раннее закрытие. При "
                        + "смене remaining проверь строку снимка ниже в тесте");
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
                "Снимок обязан возвращать тот же остаток, иначе после перезагрузки дух ждал бы не то время. При "
                        + "смене toMap проверь строку elapsed ниже в тесте");
        assertEquals(
                counter.elapsed(),
                restored.elapsed(),
                "Снимок обязан возвращать тот же счётчик прошедших тиков, иначе ожидание началось бы заново. При "
                        + "смене fromMap проверь строку тика ниже в тесте");
        assertFalse(
                restored.tick(),
                "Восстановленный счётчик обязан ждать ровно столько же: первый тик после восстановления не должен "
                        + "закрывать ожидание раньше срока, иначе перезагрузка сокращает сон. При смене fromMap "
                        + "проверь строку пропуска ниже в тесте");
    }

    @Test
    void fromMap_MissingKeys_ReturnsZeroRemaining() {
        WaitCounter restored = WaitCounter.fromMap(Map.of());

        assertEquals(
                0,
                restored.remaining(),
                "Снимок без ключей обязан давать нулевой остаток: старый снимок не знает, сколько ждали, а зависшее "
                        + "ожидание без конца хуже, чем немедленное завершение, иначе задача висит. При смене fromMap "
                        + "проверь строку тика ниже в тесте");
        assertTrue(
                restored.tick(),
                "Счётчик из снимка без ключей обязан завершиться на первом тике: задача не имеет права зависнуть в "
                        + "вечном ожидании, иначе дух застревает. При смене fromMap проверь строку отрицательного ниже "
                        + "в тесте");
    }

    @Test
    void start_NegativeTicks_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.start(-1),
                "Отрицательный срок ожидания обязан отказывать: счётчик завершился бы раньше, чем начался, иначе "
                        + "ожидание бессмысленно. При смене start проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть отклонённое число тиков, иначе отладка слепа. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь строку null ниже в тесте");
    }

    @Test
    void fromMap_NullState_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> WaitCounter.fromMap(null),
                "WaitCounter.fromMap(null) обязан отказывать: снимок обязан прийти картой, а null означает "
                        + "повреждение состояния задачи, иначе восстановление падает. При смене fromMap проверь строку "
                        + "нечислового ниже в тесте");
    }

    @Test
    void fromMap_NonNumericValue_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.fromMap(Map.of(WaitCounter.TARGET_KEY, "5")),
                "Нечисловое значение счётчика обязано отказывать: молчаливая подмена скрыла бы повреждённый снимок, "
                        + "иначе ожидание врёт. При смене fromMap проверь строку ключа ниже в тесте");

        assertTrue(
                error.getMessage().contains(WaitCounter.TARGET_KEY),
                "Отказ обязан называть повреждённый ключ, иначе отладка слепа. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь строку отрицательного elapsed ниже в тесте");
    }

    @Test
    void fromMap_NegativeElapsed_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WaitCounter.fromMap(Map.of(WaitCounter.TARGET_KEY, 5, WaitCounter.ELAPSED_KEY, -1)),
                "Отрицательный счётчик прошедшего времени обязан отказывать: снимок повреждён, иначе ожидание "
                        + "начинается с минуса. При смене fromMap проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть отклонённое число, иначе отладка слепа. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь строку выше в тесте");
    }
}
