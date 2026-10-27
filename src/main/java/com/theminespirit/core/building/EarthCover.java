package com.theminespirit.core.building;

import java.util.Objects;

public final class EarthCover {
    private static final String DIRT = "dirt";
    private static final String GRASS = "grass_block";

    private EarthCover() {}

    public static boolean covers(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок схемы не может быть null: сверяется клетка схемы");
        return DIRT.equals(SchematicBlocks.path(wanted)) && GRASS.equals(SchematicBlocks.path(actual));
    }
}
