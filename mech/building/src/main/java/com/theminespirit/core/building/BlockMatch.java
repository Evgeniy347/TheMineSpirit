package com.theminespirit.core.building;

import java.util.Objects;

public final class BlockMatch {
    private BlockMatch() {}

    public static boolean matches(String actual, String wanted) {
        Objects.requireNonNull(actual, "Стоящий блок не может быть null: сверяется клетка мира");
        Objects.requireNonNull(wanted, "Блок плана не может быть null: сверяется клетка плана");
        String standing = path(actual);
        String expected = path(wanted);
        return standing.equals(expected)
                || WoodSpecies.sameRole(standing, expected)
                || EarthCover.covers(standing, expected)
                || ("torch".equals(expected) && "wall_torch".equals(standing))
                || ("wall_torch".equals(expected) && "torch".equals(standing))
                || sameTree(standing, expected);
    }

    private static String path(String blockId) {
        String path = SchematicBlocks.path(blockId);
        int state = path.indexOf('[');
        return state < 0 ? path : path.substring(0, state);
    }

    private static boolean sameTree(String standing, String expected) {
        String sapling = standing.endsWith("_sapling") ? standing : expected.endsWith("_sapling") ? expected : null;
        if (sapling == null) {
            return false;
        }
        String tree = sapling.equals(standing) ? expected : standing;
        String species = sapling.substring(0, sapling.length() - "_sapling".length());
        return tree.equals(species + "_log") || tree.equals(species + "_wood") || tree.equals(species + "_leaves");
    }
}
