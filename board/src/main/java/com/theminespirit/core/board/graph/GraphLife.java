package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record GraphLife(
        double fullPressureMobs,
        double pressureBase,
        double pressureWound,
        double hurtShare,
        double injuryWound,
        double injuryHurt,
        double threatRadius,
        long damageWindowTicks,
        double weakHealth,
        int weakFood) {
    public static final GraphLife DEFAULTS = new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 200, 0.35, 6);

    public GraphLife {
        for (var value : new double[] {
            fullPressureMobs, pressureBase, pressureWound, hurtShare, injuryWound, injuryHurt, threatRadius, weakHealth
        }) {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Коэффициент формулы жизни " + value
                        + ": он неотрицателен и конечен, иначе приоритет жизни не посчитать");
            }
        }
        if (fullPressureMobs <= 0 || threatRadius <= 0 || damageWindowTicks <= 0) {
            throw new IllegalArgumentException("Опорные значения жизни: мобов " + fullPressureMobs + ", радиус "
                    + threatRadius + ", окно урона " + damageWindowTicks + " — все положительны");
        }
        if (weakHealth < 0 || weakHealth > 1) {
            throw new IllegalArgumentException("Порог слабого игрока " + weakHealth + " вне [0, 1]: это доля здоровья");
        }
        if (weakFood < 0) {
            throw new IllegalArgumentException(
                    "Порог еды игрока " + weakFood + " отрицателен: голод начинается с нуля");
        }
    }

    static GraphLife parse(JsonNode root) {
        if (root == null || root.isMissingNode() || !root.isObject()) {
            return DEFAULTS;
        }
        return new GraphLife(
                root.path("fullPressureMobs").asDouble(DEFAULTS.fullPressureMobs()),
                root.path("pressureBase").asDouble(DEFAULTS.pressureBase()),
                root.path("pressureWound").asDouble(DEFAULTS.pressureWound()),
                root.path("hurtShare").asDouble(DEFAULTS.hurtShare()),
                root.path("injuryWound").asDouble(DEFAULTS.injuryWound()),
                root.path("injuryHurt").asDouble(DEFAULTS.injuryHurt()),
                root.path("threatRadius").asDouble(DEFAULTS.threatRadius()),
                root.path("damageWindowTicks").asLong(DEFAULTS.damageWindowTicks()),
                root.path("weakHealth").asDouble(DEFAULTS.weakHealth()),
                root.path("weakFood").asInt(DEFAULTS.weakFood()));
    }

    public static GraphLife of(SupplyGraph graph) {
        Objects.requireNonNull(graph, "Граф не может быть null: формула жизни берётся из его данных");
        return graph.costs().life();
    }
}
