package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;

public record GraphLife(
        int damageWindowTicks,
        double threatRadius,
        double fullPressureMobs,
        double pressureBase,
        double pressureWound,
        double hurtShare,
        double injuryWound,
        double injuryHurt,
        double weakPlayerSeverity,
        float weakHealth,
        int weakFood) {
    public static final GraphLife NONE = new GraphLife(1, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0F, 0);

    static GraphLife parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            return NONE;
        }
        return new GraphLife(
                root.path("damageWindowTicks").asInt(200),
                root.path("threatRadius").asDouble(16.0),
                root.path("fullPressureMobs").asDouble(1.5),
                root.path("pressureBase").asDouble(0.4),
                root.path("pressureWound").asDouble(0.7),
                root.path("hurtShare").asDouble(0.2),
                root.path("injuryWound").asDouble(0.8),
                root.path("injuryHurt").asDouble(0.4),
                root.path("weakPlayerSeverity").asDouble(0.9),
                (float) root.path("weakHealth").asDouble(0.35),
                root.path("weakFood").asInt(6));
    }

    public GraphLife {
        if (damageWindowTicks < 1) {
            throw new IllegalArgumentException(
                    "Окно урона " + damageWindowTicks + " тиков: оно положительно, иначе деление на ноль");
        }
        if (!(threatRadius > 0) || !(fullPressureMobs > 0)) {
            throw new IllegalArgumentException("Радиус угрозы " + threatRadius + " и полное давление "
                    + fullPressureMobs + " обязаны быть положительными: близость нормируется ими");
        }
        for (var value : new double[] {
            pressureBase, pressureWound, hurtShare, injuryWound, injuryHurt, weakPlayerSeverity, weakHealth
        }) {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(
                        "Коэффициент жизни " + value + " отрицателен или не число: опасность не бывает минусом");
            }
        }
        if (weakHealth > 1) {
            throw new IllegalArgumentException(
                    "Порог слабого здоровья " + weakHealth + " больше 1: доля здоровья живёт в [0, 1]");
        }
        if (weakFood < 0) {
            throw new IllegalArgumentException(
                    "Порог слабой еды " + weakFood + " отрицателен: голод начинается с нуля");
        }
    }
}
