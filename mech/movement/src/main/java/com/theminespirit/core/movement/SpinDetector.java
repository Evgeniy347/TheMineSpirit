package com.theminespirit.core.movement;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class SpinDetector {
    public enum Kind {
        TURN,
        CIRCLE
    }

    public record Spin(long startTick, long endTick, float degrees, double shift, double path, Kind kind) {
        public long ticks() {
            return endTick - startTick;
        }

        public String text() {
            return String.format(
                    Locale.ROOT, "%.0f° за %d тиков, сдвиг %.2f, путь %.2f", degrees, ticks(), shift, path);
        }
    }

    private static final int WINDOW = 60;
    private static final int TURN_WINDOW = 40;
    private static final int END_GRACE = 20;
    private static final int COOLDOWN = 600;
    private static final double TURN_DEGREES = 270.0;
    private static final double TURN_SHIFT = 0.5;
    private static final double CIRCLE_PATH = 2.0;
    private static final double CIRCLE_SHIFT = 1.0;

    private final double[] xs = new double[WINDOW];
    private final double[] zs = new double[WINDOW];
    private final float[] yaws = new float[WINDOW];
    private int size;
    private int head;

    private boolean active;
    private long startTick;
    private long lastSpinTick;
    private long cooldownUntil;
    private Kind kind = Kind.TURN;
    private float degrees;
    private double shift;
    private double path;

    public @Nullable Spin feed(long tick, double x, double z, float yaw) {
        push(x, z, yaw);
        Stats turn = size >= TURN_WINDOW ? stats(TURN_WINDOW) : null;
        Stats circle = size >= WINDOW ? stats(WINDOW) : null;
        Stats hit = null;
        Kind hitKind = Kind.TURN;
        if (turn != null && Math.abs(turn.turnSigned) >= TURN_DEGREES && turn.shift <= TURN_SHIFT) {
            hit = turn;
        } else if (circle != null
                && circle.path >= CIRCLE_PATH
                && circle.shift <= CIRCLE_SHIFT
                && Math.abs(circle.turnSigned) >= TURN_DEGREES) {
            hit = circle;
            hitKind = Kind.CIRCLE;
        }
        if (hit != null) {
            if (!active && tick >= cooldownUntil) {
                active = true;
                startTick = tick - (hitKind == Kind.CIRCLE ? WINDOW : TURN_WINDOW);
                kind = hitKind;
            }
            if (active) {
                lastSpinTick = tick;
                degrees = (float) Math.abs(hit.turnSigned);
                shift = hit.shift;
                path = hit.path;
                if (hitKind == Kind.CIRCLE) {
                    kind = Kind.CIRCLE;
                }
            }
            return null;
        }
        if (!active || tick - lastSpinTick < END_GRACE) {
            return null;
        }
        active = false;
        cooldownUntil = tick + COOLDOWN;
        return new Spin(startTick, lastSpinTick, degrees, shift, path, kind);
    }

    public boolean active() {
        return active;
    }

    private void push(double x, double z, float yaw) {
        xs[head] = x;
        zs[head] = z;
        yaws[head] = yaw;
        head = (head + 1) % WINDOW;
        if (size < WINDOW) {
            size++;
        }
    }

    private Stats stats(int count) {
        int first = (head - count + WINDOW) % WINDOW;
        double path = 0.0;
        double turnAbs = 0.0;
        double turnSigned = 0.0;
        double prevYaw = yaws[first];
        for (int i = 1; i < count; i++) {
            int a = (first + i - 1) % WINDOW;
            int b = (first + i) % WINDOW;
            path += Math.hypot(xs[b] - xs[a], zs[b] - zs[a]);
            double delta = normalize(yaws[b] - prevYaw);
            turnAbs += Math.abs(delta);
            turnSigned += delta;
            prevYaw = yaws[b];
        }
        int last = (first + count - 1) % WINDOW;
        double shift = Math.hypot(xs[last] - xs[first], zs[last] - zs[first]);
        return new Stats(path, shift, turnAbs, turnSigned);
    }

    private static double normalize(double degrees) {
        double value = degrees % 360.0;
        if (value > 180.0) {
            value -= 360.0;
        } else if (value < -180.0) {
            value += 360.0;
        }
        return value;
    }

    private record Stats(double path, double shift, double turnAbs, double turnSigned) {}
}
