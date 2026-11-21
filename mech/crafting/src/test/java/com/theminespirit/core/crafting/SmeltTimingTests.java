package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SmeltTimingTests {
    @Test
    void expectedTicks_ThreeItemsOfTwoHundredTicks_SixHundred() {
        assertEquals(
                600,
                SmeltTiming.expectedTicks(3, 200),
                "Три предмета по 200 тиков плавятся 600 тиков подряд: печь греет по одному, и ожидание считается "
                        + "как число предметов умножить на время рецепта, иначе тайм-аут ставится на один предмет и "
                        + "печь срывается до готовности очереди. При смене SmeltTiming.expectedTicks проверь "
                        + "умножение на счётчик в строке выше");
    }

    @Test
    void expectedTicks_RecipeCookTime_OverridesDefaultFurnaceTime() {
        assertEquals(
                300,
                SmeltTiming.expectedTicks(3, 100),
                "Время плавки берётся из рецепта, а не из 200 тиков обычной печи: иначе ожидание вдвое длиннее "
                        + "нужного и зависшая печь терпит лишние 300 тиков. При смене источника времени в "
                        + "SmeltTiming.expectedTicks проверь строку 200 выше в этом классе");
    }

    @Test
    void timeoutTicks_ThreeItems_AddsFourHundredReserve() {
        assertEquals(
                1000,
                SmeltTiming.timeoutTicks(3, 200),
                "Тайм-аут плавки равен ожидаемому времени 600 плюс запас 400 тиков: меньший запас роняет плавку "
                        + "из-за опроса раз в 10 тиков и подбора, больший прячет зависшую печь от перезапуска, "
                        + "иначе она висит. При "
                        + "смене запаса в SmeltTiming.timeoutTicks проверь ту же добавку в "
                        + "campfireTimeoutTicks_FiveItems_AddsFourHundredReserve");
    }

    @Test
    void expectedTicks_ZeroCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.expectedTicks(0, 200),
                "Ноль предметов обязан отвергаться: иначе ждать нечего, а тайм-аут получился бы равен одному "
                        + "запасу и плавка пустой очереди висела бы 400 тиков. При смене проверки счётчика в "
                        + "SmeltTiming.expectedTicks проверь строку нулевого времени ниже в тесте");
    }

    @Test
    void expectedTicks_ZeroCookTicks_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.expectedTicks(3, 0),
                "Нулевое время плавки обязано отвергаться: иначе ожидание нулевой длины сорвёт работающую "
                        + "плавку тайм-аутом в первый же опрос. При смене проверки времени в SmeltTiming.expectedTicks "
                        + "проверь строку нулевого счёта выше в тесте");
    }

    @Test
    void campfireExpectedTicks_FourItems_CookInParallel() {
        assertEquals(
                600,
                SmeltTiming.campfireExpectedTicks(4, 600),
                "Четыре предмета на костре готовятся одновременно за одно время рецепта, а не за четыре: иначе "
                        + "тайм-аут вчетверо длиннее и зависший костёр не замечают. При смене "
                        + "SmeltTiming.campfireExpectedTicks проверь две партии в строке ниже в тесте");
    }

    @Test
    void campfireExpectedTicks_FiveItems_NeedTwoBatches() {
        assertEquals(
                1200,
                SmeltTiming.campfireExpectedTicks(5, 600),
                "Пятый предмет ждёт освобождения слота: пять предметов — две партии по четыре слота, иначе "
                        + "ожидание берётся за одну партию и тайм-аут срывает вторую. При смене "
                        + "SmeltTiming.campfireExpectedTicks проверь четыре предмета выше в тесте");
    }

    @Test
    void campfireTimeoutTicks_FiveItems_AddsFourHundredReserve() {
        assertEquals(
                1600,
                SmeltTiming.campfireTimeoutTicks(5, 600),
                "Тайм-аут костра равен ожидаемому времени 1200 плюс запас 400 тиков на опрос и подбор выпавших "
                        + "предметов: без запаса готовая партия не успевает подобраться, иначе предметы пропадут. При смене "
                        + "SmeltTiming.campfireTimeoutTicks проверь запас в строке timeoutTicks выше в тесте");
    }

    @Test
    void campfireExpectedTicks_ZeroCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.campfireExpectedTicks(0, 600),
                "Ноль предметов на костре обязан отвергаться: ждать нечего, и тайм-аут равен одному запасу, "
                        + "иначе пустая очередь держит слота костра и блокирует заказы. При смене проверки "
                        + "счётчика в SmeltTiming.campfireExpectedTicks проверь строку четырёх предметов выше");
    }
}
