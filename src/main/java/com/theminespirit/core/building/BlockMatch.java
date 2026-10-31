package com.theminespirit.core.building;

import java.util.Objects;

public final class BlockMatch {
    private BlockMatch() {}

    public static boolean matches(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок плана не может быть null: сверяется клетка плана");
        String standing = SchematicBlocks.path(actual);
        String expected = SchematicBlocks.path(wanted);
        return standing.equals(expected)
                || WoodSpecies.sameRole(actual, wanted)
                || EarthCover.covers(actual, wanted)
                || ("torch".equals(expected) && "wall_torch".equals(standing));
    }
}
