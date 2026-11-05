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
                "Три предмета по 200 тиков плавятся 600 тиков подряд: печь греет по одному");
    }

    @Test
    void expectedTicks_RecipeCookTime_OverridesDefaultFurnaceTime() {
        assertEquals(
                300,
                SmeltTiming.expectedTicks(3, 100),
                "Время плавки берётся из рецепта, а не из 200 обычной печи: иначе ожидание вдвое длиннее нужного");
    }

    @Test
    void timeoutTicks_ThreeItems_AddsFourHundredReserve() {
        assertEquals(
                1000,
                SmeltTiming.timeoutTicks(3, 200),
                "Тайм-аут плавки = ожидаемое время (600) + запас 400 тиков: меньший запас роняет плавку из-за "
                        + "опроса раз в 10 тиков и подбора, больший прячет зависшую печь");
    }

    @Test
    void expectedTicks_ZeroCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.expectedTicks(0, 200),
                "Ноль предметов обязан отвергаться: ждать нечего, а тайм-аут получился бы равен одному запасу");
    }

    @Test
    void expectedTicks_ZeroCookTicks_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.expectedTicks(3, 0),
                "Нулевое время плавки обязано отвергаться: ожидание нулевой длины сорвёт плавку тайм-аутом");
    }

    @Test
    void campfireExpectedTicks_FourItems_CookInParallel() {
        assertEquals(
                600,
                SmeltTiming.campfireExpectedTicks(4, 600),
                "Четыре предмета на костре готовятся одновременно за одно время рецепта, а не за четыре");
    }

    @Test
    void campfireExpectedTicks_FiveItems_NeedTwoBatches() {
        assertEquals(
                1200,
                SmeltTiming.campfireExpectedTicks(5, 600),
                "Пятый предмет ждёт освобождения слота: пять предметов — две партии по четыре слота");
    }

    @Test
    void campfireTimeoutTicks_FiveItems_AddsFourHundredReserve() {
        assertEquals(
                1600,
                SmeltTiming.campfireTimeoutTicks(5, 600),
                "Тайм-аут костра = ожидаемое время (1200) + запас 400 тиков на опрос и подбор выпавших предметов");
    }

    @Test
    void campfireExpectedTicks_ZeroCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltTiming.campfireExpectedTicks(0, 600),
                "Ноль предметов на костре обязан отвергаться: ждать нечего");
    }
}
