package com.theminespirit.core.experience;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class ExperienceShareTests {
    @Test
    void split_AmountDivisibleByPlayers_EveryShareIsEqual() {
        assertArrayEquals(
                new int[] {10, 10, 10, 10},
                ExperienceShare.split(40, 4),
                "Опыт 40 на 4 игроков обязан делиться ровно по 10, иначе один получил бы больше при равных правах. "
                        + "При смене ExperienceShare.split проверь строку остатка ниже в тесте");
    }

    @Test
    void split_AmountNotDivisible_RemainderGoesToOneEachInOrder() {
        assertArrayEquals(
                new int[] {4, 4, 3, 3},
                ExperienceShare.split(14, 4),
                "14 на 4 — доли 3 с остатком 2: лишние единицы обязаны достаться первым по порядку, а не всем поровну "
                        + "дробно, иначе делёж зависит от порядка записи. При смене split проверь строку нуля ниже в "
                        + "тесте");
    }

    @Test
    void split_ZeroPlayers_ReturnsEmptyShares() {
        assertArrayEquals(
                new int[0],
                ExperienceShare.split(7, 0),
                "Ноль игроков — опыт копится в пуле поселения: долей нет, сумма сохраняется в пуле, а не теряется, "
                        + "иначе опыт пропадает. При смене split проверь строку нулевого опыта ниже в тесте");
    }

    @Test
    void split_ZeroAmount_ReturnsZeroShares() {
        assertArrayEquals(
                new int[] {0, 0, 0},
                ExperienceShare.split(0, 3),
                "Нулевой опыт обязан дать три нулевые доли: сумма 0 сохраняется, игрок не получает «пустого» "
                        + "начисления, иначе интерфейс врёт. При смене split проверь строку отрицательного ниже в "
                        + "тесте");
    }

    @Test
    void split_NegativeAmount_Throws() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> ExperienceShare.split(-1, 3),
                "Отрицательный опыт — ошибка вызывающего: молча делить отрицательное значит списывать опыт у игрока, "
                        + "иначе ошибка маскируется. При смене проверки проверь строку имени ниже в тесте");
        assertTrue(
                thrown.getMessage().contains("-1"),
                "Сообщение об ошибке обязано называть величину -1, чтобы было видно, откуда пришло: "
                        + thrown.getMessage() + ", иначе отладка слепа. При смене текста проверь строку игроков ниже "
                        + "в тесте");
    }

    @Test
    void split_NegativePlayers_Throws() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> ExperienceShare.split(10, -2),
                "Отрицательное число игроков — ошибка вызывающего: пустой список задают нулём, а не минусом, иначе "
                        + "деление на отрицательное. При смене проверки проверь строку имени ниже в тесте");
        assertTrue(
                thrown.getMessage().contains("-2"),
                "Сообщение об ошибке обязано называть число -2: " + thrown.getMessage() + ", иначе отладка слепа. При "
                        + "смене текста проверь строку меньше ниже в тесте");
    }

    @Test
    void split_FewerAmountThanPlayers_BaseIsZeroAndRemainderCarriesAll() {
        assertArrayEquals(
                new int[] {1, 1, 1, 0, 0},
                ExperienceShare.split(3, 5),
                "3 на 5 — база 0, остаток 3: трое первых получают по 1, остальные по 0, сумма 3 не теряется, иначе "
                        + "опыт пропадает. При смене split проверь строку суммы ниже в тесте");
    }

    @Property
    void split_AnyAmountAndPlayers_SumEqualsAmountAndSharesDifferByAtMostOne(
            @ForAll @IntRange(min = 0, max = 100_000) int amount, @ForAll @IntRange(min = 1, max = 64) int players) {
        int[] shares = ExperienceShare.split(amount, players);

        assertEquals(
                players,
                shares.length,
                "Долей обязано быть ровно столько же, сколько игроков (" + players + "), иначе кто-то не получит "
                        + "опыт. При смене split проверь строку суммы ниже в тесте");
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int share : shares) {
            sum += share;
            min = Math.min(min, share);
            max = Math.max(max, share);
        }
        assertEquals(
                amount,
                sum,
                "Сумма долей обязана равняться исходному опыту " + amount + ": остаток от деления не теряется, иначе "
                        + "опыт исчезает. При смене split проверь строку разброса ниже в тесте");
        assertTrue(
                max - min <= 1,
                "Доли обязаны отличаться не больше чем на 1: при " + amount + " на " + players + " разброс " + min
                        + ".." + max + " значит, что делёж не поровну, иначе игроки жалуются. При смене split проверь "
                        + "строку выше в тесте");
    }
}
