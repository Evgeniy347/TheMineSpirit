package com.theminespirit.core.building;

import java.util.Objects;

public final class PathGround {
    private static final String AIR = "minecraft:air";

    private PathGround() {}

    public static boolean airCell(String cellId) {
        Objects.requireNonNull(cellId, "Блок клетки не может быть null: по нему видно, свободна ли она");
        return AIR.equals(cellId);
    }

    public static boolean raisedPath(String cellId, String belowId) {
        Objects.requireNonNull(cellId, "Блок клетки не может быть null: по нему видно, свободна ли она");
        Objects.requireNonNull(belowId, "Блок под клеткой не может быть null: по нему видно, есть ли опора");
        return AIR.equals(cellId) && SchematicActions.isTrampleGround(belowId);
    }

    public static boolean openAbove(String blockId) {
        Objects.requireNonNull(blockId, "Блок над землёй не может быть null: по нему решается, топтать ли дорожку");
        return AIR.equals(blockId);
    }
}
