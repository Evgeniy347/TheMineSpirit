package com.theminespirit.neoforge;

import com.theminespirit.core.reload.DataTables;

/**
 * Темп постановки блоков стройкой по схеме: интервал из горячей таблицы {@code building/build.json}. Сервер GameTest
 * подменяет его на 1 тик для всех пачек, кроме пачки, проверяющей сам темп: геометрию стройки проверяют десятки
 * сцен, и ожидание 10 тиков на блок умножило бы прогон без пользы для проверки (docs/TESTING.md).
 */
public final class BuildPace {
    private static final int NO_OVERRIDE = -1;
    private static volatile int override = NO_OVERRIDE;

    private BuildPace() {
    }

    public static int intervalTicks() {
        int forced = override;
        return forced == NO_OVERRIDE ? DataTables.INSTANCE.building().current().placeIntervalTicks() : forced;
    }

    public static void overrideForGameTest(int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Темп " + ticks + " тиков: между постановками хотя бы 1 тик");
        }
        override = ticks;
    }

    public static void useTable() {
        override = NO_OVERRIDE;
    }
}
