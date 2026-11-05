package com.theminespirit.neoforge;

import java.util.Locale;
import java.util.Optional;

public final class TickMeter {
    public static final long WINDOW_TICKS = 1200;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final TickMeter DEFAULT = new TickMeter(WINDOW_TICKS);
    private static long started;
    private static boolean running;

    private final long windowTicks;
    private long ticks;
    private long totalNanos;
    private long worstNanos;

    TickMeter(long windowTicks) {
        if (windowTicks < 1) {
            throw new IllegalArgumentException("Окно сводки " + windowTicks + " тиков: нужен хотя бы один");
        }
        this.windowTicks = windowTicks;
    }

    public static void start() {
        started = System.nanoTime();
        running = true;
    }

    public static Optional<String> finish() {
        if (!running) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        running = false;
        return DEFAULT.observe(now - started);
    }

    Optional<String> observe(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException(
                    "Длительность тика " + elapsedNanos + " нс отрицательна: время идёт вперёд");
        }
        ticks++;
        totalNanos += elapsedNanos;
        worstNanos = Math.max(worstNanos, elapsedNanos);
        if (ticks < windowTicks) {
            return Optional.empty();
        }
        String line = line(ticks, totalNanos, worstNanos);
        ticks = 0;
        totalNanos = 0;
        worstNanos = 0;
        return Optional.of(line);
    }

    static String line(long ticks, long totalNanos, long worstNanos) {
        if (ticks < 1 || totalNanos < 0 || worstNanos < 0) {
            throw new IllegalArgumentException(
                    "Сводка тиков " + ticks + "/" + totalNanos + "/" + worstNanos + " не сходится: счётчики вперёд");
        }
        double averageMillis = totalNanos / NANOS_PER_MILLI / ticks;
        double worstMillis = worstNanos / NANOS_PER_MILLI;
        double seconds = totalNanos / 1_000_000_000.0;
        return String.format(
                Locale.ROOT,
                "[tick] MSPT %.1f, худший %.1f за %d тиков (%.0f с)",
                averageMillis,
                worstMillis,
                ticks,
                seconds);
    }
}
