package com.theminespirit.core.script;

import com.theminespirit.core.movement.RoutePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PatrolParams(List<BlockCoord> points, RoutePlan.Mode mode, int loops) {
    public PatrolParams {
        Objects.requireNonNull(points, "Точки маршрута не могут быть null: без них патрулю некуда идти");
        Objects.requireNonNull(
                mode, "Режим маршрута не может быть null: без него неизвестно, кругом идти или туда-обратно");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Маршрут без точек: патрулю некуда идти");
        }
        if (points.size() > CatalogLimits.NAV_PATROL_POINTS_MAX) {
            throw new IllegalArgumentException("Точек маршрута " + points.size() + ", а допущено не больше "
                    + CatalogLimits.NAV_PATROL_POINTS_MAX + ": длинный маршрут режьте на части");
        }
        if (loops < CatalogLimits.NAV_PATROL_LOOPS_MIN || loops > CatalogLimits.NAV_PATROL_LOOPS_MAX) {
            throw new IllegalArgumentException("Число кругов " + loops + " вне диапазона ["
                    + CatalogLimits.NAV_PATROL_LOOPS_MIN + ", " + CatalogLimits.NAV_PATROL_LOOPS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static PatrolParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        List<BlockCoord> points = new ArrayList<>();
        for (String text : ScriptArgs.requireStringList(params, "points")) {
            points.add(parsePoint(text));
        }
        return new PatrolParams(
                points, parseMode(ScriptArgs.requireString(params, "mode")), ScriptArgs.requireInt(params, "loops"));
    }

    static BlockCoord parsePoint(String text) {
        Objects.requireNonNull(text, "Точка маршрута не может быть null: каждая точка задаётся строкой 'x,y,z'");
        String[] parts = text.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Точка маршрута '" + text + "' не вида 'x,y,z': патруль не знает, куда идти");
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            if (x < -CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    || x > CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    || z < -CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    || z > CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    || y < CatalogLimits.WORLD_Y_MIN
                    || y > CatalogLimits.WORLD_Y_MAX) {
                throw new IllegalArgumentException(
                        "Точка маршрута '" + text + "' вне границ мира: патрулю туда не дойти");
            }
            return new BlockCoord(x, y, z);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Точка маршрута '" + text + "' не вида 'x,y,z' из целых чисел: патруль не знает, куда идти", error);
        }
    }

    static RoutePlan.Mode parseMode(String text) {
        return switch (text) {
            case "loop" -> RoutePlan.Mode.LOOP;
            case "ping_pong" -> RoutePlan.Mode.PING_PONG;
            default ->
                throw new IllegalArgumentException(
                        "Режим маршрута '" + text + "' неизвестен: допустимые loop, ping_pong");
        };
    }
}
