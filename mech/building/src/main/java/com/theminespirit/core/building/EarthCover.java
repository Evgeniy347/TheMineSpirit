package com.theminespirit.core.building;

import java.util.Objects;

public final class EarthCover {
    public static final String DIRT = "dirt";
    public static final String GRASS = "grass_block";

    private EarthCover() {}

    public static boolean earth(String block) {
        Objects.requireNonNull(block, "Блок земли не может быть null: без него не отличить грунт");
        String path = SchematicBlocks.path(block);
        return DIRT.equals(path) || GRASS.equals(path);
    }

    public static boolean covers(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок схемы не может быть null: сверяется клетка схемы");
        return earth(actual) && earth(wanted) && !SchematicBlocks.path(actual).equals(SchematicBlocks.path(wanted));
    }

    public static String restored(String block) {
        Objects.requireNonNull(block, "Блок пролома не может быть null: без него неизвестно, что вернуть");
        if (!earth(block) || DIRT.equals(SchematicBlocks.path(block))) {
            return block;
        }
        return "minecraft:" + DIRT;
    }
}
