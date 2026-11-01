package com.theminespirit.core.building;

import java.util.Objects;

public final class PathGround {
    private static final String AIR = "minecraft:air";

    private PathGround() {}

    public static boolean openAbove(String blockId) {
        Objects.requireNonNull(blockId, "Блок над землёй не может быть null: по нему решается, топтать ли дорожку");
        return AIR.equals(blockId);
    }
}
