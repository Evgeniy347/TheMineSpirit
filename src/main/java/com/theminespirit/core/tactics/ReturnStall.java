package com.theminespirit.core.tactics;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ReturnStall {
    public static final int STALL_TICKS = 200;
    private static final double PROGRESS = 0.5;

    private @Nullable Point point;
    private double best;
    private int ticks;

    public boolean stalled(Point at, double distance) {
        Objects.requireNonNull(at, "Точка якоря не может быть null: срыв возврата считается по ней");
        if (!at.equals(point)) {
            point = at;
            best = distance;
            ticks = 0;
            return false;
        }
        if (distance < best - PROGRESS) {
            best = distance;
            ticks = 0;
            return false;
        }
        ticks++;
        return ticks >= STALL_TICKS;
    }

    public int ticks() {
        return ticks;
    }

    public void reset() {
        point = null;
        best = 0;
        ticks = 0;
    }
}
