package com.theminespirit.core.movement.api;

public final class BodyTurn {
    private BodyTurn() {}

    public static float limit(float from, float to, float maxStep) {
        if (maxStep < 0.0F) {
            throw new IllegalArgumentException("Предел доворота тела " + maxStep
                    + " отрицателен: отрицательный шаг не даёт духу повернуться, и он пойдёт спиной вперёд");
        }
        float delta = wrap(to - from);
        float step = Math.max(-maxStep, Math.min(maxStep, delta));
        return wrap(from + step);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }
}
