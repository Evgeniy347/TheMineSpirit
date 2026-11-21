package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoardClockTests {

    @Test
    void moment_StartOfWorld_IsFirstDaySixOClock() {
        BoardClock.Moment moment = BoardClock.moment(0);

        assertEquals(
                1,
                moment.day(),
                "Нулевой тик — первый игровой день"
                        + " Иначе тест moment_StartOfWorld_IsFirstDaySixOClock перестанет ловить эту поломку. При смене moment_StartOfWorld_IsFirstDaySixOClock проверь это утверждение.");
        assertEquals(
                6,
                moment.hour(),
                "Игровые сутки начинаются в 06:00"
                        + " Иначе тест moment_StartOfWorld_IsFirstDaySixOClock перестанет ловить эту поломку. При смене moment_StartOfWorld_IsFirstDaySixOClock проверь это утверждение.");
        assertEquals(
                0,
                moment.minute(),
                "Нулевая минута часа"
                        + " Иначе тест moment_StartOfWorld_IsFirstDaySixOClock перестанет ловить эту поломку. При смене moment_StartOfWorld_IsFirstDaySixOClock проверь это утверждение.");
    }

    @Test
    void moment_NoonAndMidnight_FollowMinecraftClock() {
        assertEquals(
                12,
                BoardClock.moment(6000).hour(),
                "6000 тиков после рассвета — полдень"
                        + " Иначе тест moment_NoonAndMidnight_FollowMinecraftClock перестанет ловить эту поломку. При смене moment_NoonAndMidnight_FollowMinecraftClock проверь это утверждение.");
        assertEquals(
                18,
                BoardClock.moment(12000).hour(),
                "12000 тиков — закат"
                        + " Иначе тест moment_NoonAndMidnight_FollowMinecraftClock перестанет ловить эту поломку. При смене moment_NoonAndMidnight_FollowMinecraftClock проверь это утверждение.");
        assertEquals(
                0,
                BoardClock.moment(18000).hour(),
                "18000 тиков — полночь"
                        + " Иначе тест moment_NoonAndMidnight_FollowMinecraftClock перестанет ловить эту поломку. При смене moment_NoonAndMidnight_FollowMinecraftClock проверь это утверждение.");
    }

    @Test
    void moment_SecondDay_CountsDaysFromOne() {
        BoardClock.Moment moment = BoardClock.moment(24000 + 1000);

        assertEquals(
                2,
                moment.day(),
                "Следующие сутки — второй день"
                        + " Иначе тест moment_SecondDay_CountsDaysFromOne перестанет ловить эту поломку. При смене moment_SecondDay_CountsDaysFromOne проверь это утверждение.");
        assertEquals(
                7,
                moment.hour(),
                "Час считается внутри суток"
                        + " Иначе тест moment_SecondDay_CountsDaysFromOne перестанет ловить эту поломку. При смене moment_SecondDay_CountsDaysFromOne проверь это утверждение.");
        assertEquals(
                0,
                moment.minute(),
                "Ровный час — нулевая минута"
                        + " Иначе тест moment_SecondDay_CountsDaysFromOne перестанет ловить эту поломку. При смене moment_SecondDay_CountsDaysFromOne проверь это утверждение.");
    }

    @Test
    void moment_HalfHour_IsThirtyMinutes() {
        assertEquals(
                30,
                BoardClock.moment(500).minute(),
                "Половина игрового часа — 30 минут"
                        + " Иначе тест moment_HalfHour_IsThirtyMinutes перестанет ловить эту поломку. При смене moment_HalfHour_IsThirtyMinutes проверь это утверждение.");
        assertEquals(
                59,
                BoardClock.moment(999).minute(),
                "Последний тик часа — 59 минут"
                        + " Иначе тест moment_HalfHour_IsThirtyMinutes перестанет ловить эту поломку. При смене moment_HalfHour_IsThirtyMinutes проверь это утверждение.");
    }

    @Test
    void twoDigits_SingleDigit_PadsWithZero() {
        assertEquals(
                "06",
                BoardClock.twoDigits(6),
                "Часы и минуты подписываются двумя цифрами"
                        + " Иначе тест twoDigits_SingleDigit_PadsWithZero перестанет ловить эту поломку. При смене twoDigits_SingleDigit_PadsWithZero проверь это утверждение.");
        assertEquals(
                "23",
                BoardClock.twoDigits(23),
                "Двузначное число не меняется"
                        + " Иначе тест twoDigits_SingleDigit_PadsWithZero перестанет ловить эту поломку. При смене twoDigits_SingleDigit_PadsWithZero проверь это утверждение.");
    }

    @Test
    void spentMinutes_PartialMinute_RoundsUp() {
        assertEquals(
                0L,
                BoardClock.spentMinutes(0),
                "Без затраты минут нет"
                        + " Иначе тест spentMinutes_PartialMinute_RoundsUp перестанет ловить эту поломку. При смене spentMinutes_PartialMinute_RoundsUp проверь это утверждение.");
        assertEquals(
                1L,
                BoardClock.spentMinutes(1),
                "Начатая минута считается целой"
                        + " Иначе тест spentMinutes_PartialMinute_RoundsUp перестанет ловить эту поломку. При смене spentMinutes_PartialMinute_RoundsUp проверь это утверждение.");
        assertEquals(
                1L,
                BoardClock.spentMinutes(1200),
                "Ровная минута — одна"
                        + " Иначе тест spentMinutes_PartialMinute_RoundsUp перестанет ловить эту поломку. При смене spentMinutes_PartialMinute_RoundsUp проверь это утверждение.");
        assertEquals(
                2L,
                BoardClock.spentMinutes(1201),
                "Следующий тик начинает вторую минуту"
                        + " Иначе тест spentMinutes_PartialMinute_RoundsUp перестанет ловить эту поломку. При смене spentMinutes_PartialMinute_RoundsUp проверь это утверждение.");
    }
}
