package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoardClockTests {

    @Test
    void moment_StartOfWorld_IsFirstDaySixOClock() {
        BoardClock.Moment moment = BoardClock.moment(0);

        assertEquals(1, moment.day(), "Нулевой тик — первый игровой день");
        assertEquals(6, moment.hour(), "Игровые сутки начинаются в 06:00");
        assertEquals(0, moment.minute(), "Нулевая минута часа");
    }

    @Test
    void moment_NoonAndMidnight_FollowMinecraftClock() {
        assertEquals(12, BoardClock.moment(6000).hour(), "6000 тиков после рассвета — полдень");
        assertEquals(18, BoardClock.moment(12000).hour(), "12000 тиков — закат");
        assertEquals(0, BoardClock.moment(18000).hour(), "18000 тиков — полночь");
    }

    @Test
    void moment_SecondDay_CountsDaysFromOne() {
        BoardClock.Moment moment = BoardClock.moment(24000 + 1000);

        assertEquals(2, moment.day(), "Следующие сутки — второй день");
        assertEquals(7, moment.hour(), "Час считается внутри суток");
        assertEquals(0, moment.minute(), "Ровный час — нулевая минута");
    }

    @Test
    void moment_HalfHour_IsThirtyMinutes() {
        assertEquals(30, BoardClock.moment(500).minute(), "Половина игрового часа — 30 минут");
        assertEquals(59, BoardClock.moment(999).minute(), "Последний тик часа — 59 минут");
    }

    @Test
    void twoDigits_SingleDigit_PadsWithZero() {
        assertEquals("06", BoardClock.twoDigits(6), "Часы и минуты подписываются двумя цифрами");
        assertEquals("23", BoardClock.twoDigits(23), "Двузначное число не меняется");
    }

    @Test
    void spentMinutes_PartialMinute_RoundsUp() {
        assertEquals(0L, BoardClock.spentMinutes(0), "Без затраты минут нет");
        assertEquals(1L, BoardClock.spentMinutes(1), "Начатая минута считается целой");
        assertEquals(1L, BoardClock.spentMinutes(1200), "Ровная минута — одна");
        assertEquals(2L, BoardClock.spentMinutes(1201), "Следующий тик начинает вторую минуту");
    }
}
