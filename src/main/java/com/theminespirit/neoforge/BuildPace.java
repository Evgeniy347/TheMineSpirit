package com.theminespirit.neoforge;

import com.theminespirit.core.reload.DataTables;

/**
 * Темп постановки блоков стройкой по схеме: интервал из горячей таблицы {@code building/build.json}. Сервер GameTest
 * подменяет его на 1 тик для всех пачек, кроме пачки, проверяющей сам темп: геометрию стройки проверяют десятки
 * сцен, и ожидание 10 тиков на блок умножило бы прогон без пользы для проверки (docs/TESTING.md). Демо-дух
 * ({@code /spirit demo}) строит своим темпом {@code demoPlaceIntervalTicks}.
 */
public final class BuildPace {
    private BuildPace() {}

    public static int intervalTicks(SpiritEntity spirit) {
        return DataTables.INSTANCE.building().current().intervalTicks(spirit.isDemo());
    }
}
