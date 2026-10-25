package com.theminespirit.core.ui;

public final class BoardClock {
    public static final int TICKS_PER_DAY = 24000;
    public static final int TICKS_PER_HOUR = 1000;
    public static final int TICKS_PER_MINUTE = 1200;
    public static final int HOURS_PER_DAY = 24;
    public static final int FIRST_HOUR = 6;

    private BoardClock() {}

    public record Moment(int day, int hour, int minute) {}

    public static Moment moment(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Тик " + tick + " отрицателен: игровое время идёт вперёд, а не назад");
        }
        int day = (int) (tick / TICKS_PER_DAY) + 1;
        int hour = (int) ((tick % TICKS_PER_DAY) / TICKS_PER_HOUR + FIRST_HOUR) % HOURS_PER_DAY;
        int minute = (int) ((tick % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR);
        return new Moment(day, hour, minute);
    }

    public static String twoDigits(int value) {
        if (value < 0 || value > 99) {
            throw new IllegalArgumentException(
                    "Значение " + value + " вне 0..99: двух цифр хватает только для часов " + "и минут");
        }
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    public static long spentMinutes(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException(
                    "Затрата " + ticks + " отрицательна: время работы идёт вперёд, а не назад");
        }
        return (ticks + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE;
    }
}
