package com.theminespirit.core.script;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BuildFenceParams(String block, @Nullable String gate, List<BlockCoord> points) {
    public BuildFenceParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Не задан блок забора: без него неизвестно, чем городить");
        }
        if (gate == null) {
            gate = "";
        }
        Objects.requireNonNull(points, "Точки забора не могут быть null: без углов неизвестно, где ставить контур");
        points = List.copyOf(points);
        if (points.size() < CatalogLimits.FENCE_CORNERS_MIN || points.size() > CatalogLimits.FENCE_CORNERS_MAX) {
            throw new IllegalArgumentException("Углов забора " + points.size() + ": нужно от "
                    + CatalogLimits.FENCE_CORNERS_MIN + " до " + CatalogLimits.FENCE_CORNERS_MAX
                    + ", иначе не задать замкнутый контур");
        }
        for (BlockCoord point : points) {
            Objects.requireNonNull(point, "Угол забора не может быть null: без точки не построить ребро");
            BuildingCoords.checkX(point.x());
            BuildingCoords.checkY(point.y());
            BuildingCoords.checkZ(point.z());
        }
    }

    public static BuildFenceParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BuildFenceParams(
                ScriptArgs.requireString(params, "block"),
                ScriptArgs.optionalString(params, "gate", ""),
                parsePoints(ScriptArgs.requireStringList(params, "points")));
    }

    private static List<BlockCoord> parsePoints(List<String> points) {
        List<BlockCoord> parsed = new ArrayList<>(points.size());
        for (String point : points) {
            parsed.add(parsePoint(point));
        }
        return parsed;
    }

    private static BlockCoord parsePoint(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Точка забора '" + text + "' не в формате \"x,y,z\": " + "три координаты через запятую");
        }
        return new BlockCoord(
                parseCoordinate(text, parts[0]), parseCoordinate(text, parts[1]), parseCoordinate(text, parts[2]));
    }

    private static int parseCoordinate(String point, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Точка забора '" + point + "' содержит не целое число: " + "координаты задаются целыми блоками",
                    error);
        }
    }
}
