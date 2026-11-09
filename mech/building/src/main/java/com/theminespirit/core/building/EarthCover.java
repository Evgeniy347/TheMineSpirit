package com.theminespirit.core.building;

import java.util.Objects;

public final class EarthCover {
    private static final String DIRT = "dirt";
    private static final String GRASS = "grass_block";

    private EarthCover() {}

    public static boolean covers(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок схемы не может быть null: сверяется клетка схемы");
        String standing = SchematicBlocks.path(actual);
        String expected = SchematicBlocks.path(wanted);
        return (DIRT.equals(expected) && GRASS.equals(standing)) || (GRASS.equals(expected) && DIRT.equals(standing));
    }

    public static String restoreBlock(String wanted) {
        Objects.requireNonNull(wanted, "Блок плана не может быть null: восстанавливать нечего");
        return GRASS.equals(SchematicBlocks.path(wanted)) ? DIRT : wanted;
    }
}
