package com.theminespirit.core.script;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScaffoldClearParams(
        String scaffoldId,
        int x,
        int y,
        int z,
        int height,
        String block,
        List<BlockCoord> cells,
        List<BlockCoord> tower) {
    private static final String AUTO = "auto";
    private static final String AIR = "air";

    public ScaffoldClearParams {
        Objects.requireNonNull(scaffoldId, "id подмости не может быть null: по нему снимается защита");
        if (height < 1 || height > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Высота подмости " + height + " вне 1.." + CatalogLimits.WORLD_Y_MAX);
        }
        BuildingCoords.checkX(x);
        BuildingCoords.checkY(y);
        BuildingCoords.checkZ(z);
        Objects.requireNonNull(block, "Блок башни не может быть null: без него не из чего строить");
        cells = List.copyOf(Objects.requireNonNull(cells, "Клетки целей не могут быть null"));
        tower = List.copyOf(Objects.requireNonNull(tower, "Клетки своей башни не могут быть null"));
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Подмость без целей: сносить нечего");
        }
    }

    public static ScaffoldClearParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String id = ScriptArgs.requireString(params, "scaffold");
        List<BlockCoord> cells = parseAll(ScriptArgs.requireStringList(params, "cells"));
        List<BlockCoord> tower = parseAll(ScriptArgs.requireStringList(params, "tower"));
        return new ScaffoldClearParams(
                id,
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"),
                ScriptArgs.requireInt(params, "height"),
                ScriptArgs.optionalString(params, "block", AUTO),
                cells,
                tower);
    }

    public boolean autoBlock() {
        return AUTO.equals(block) || AIR.equals(block);
    }

    private static List<BlockCoord> parseAll(List<String> texts) {
        List<BlockCoord> cells = new ArrayList<>();
        for (String text : texts) {
            cells.add(parse(text));
        }
        return cells;
    }

    private static BlockCoord parse(String text) {
        int first = text.indexOf(',');
        int second = text.indexOf(',', first + 1);
        if (first < 0 || second < 0 || text.indexOf(',', second + 1) >= 0) {
            throw new IllegalArgumentException("Клетка цели '" + text + "': нужен формат x,y,z");
        }
        return new BlockCoord(
                Integer.parseInt(text.substring(0, first).trim()),
                Integer.parseInt(text.substring(first + 1, second).trim()),
                Integer.parseInt(text.substring(second + 1).trim()));
    }
}
