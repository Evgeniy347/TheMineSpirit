package com.theminespirit.core.building;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SchematicActions {
    private static final Set<String> FLOWERS = Set.of(
            "poppy",
            "dandelion",
            "cornflower",
            "allium",
            "azure_bluet",
            "oxeye_daisy",
            "blue_orchid",
            "lily_of_the_valley",
            "wither_rose");
    private static final String DIRT_PATH = "dirt_path";
    private static final String DIRT = "dirt";
    private static final String GRASS = "grass_block";
    private static final String PODZOL = "podzol";
    private static final String COARSE_DIRT = "coarse_dirt";
    private static final String ROOTED_DIRT = "rooted_dirt";
    private static final String MYCELIUM = "mycelium";
    private static final String WOODEN_SHOVEL = "minecraft:wooden_shovel";
    private static final String STONE_SHOVEL = "minecraft:stone_shovel";

    private SchematicActions() {}

    public static boolean isActionPlaced(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, ставится ли он действием");
        return DIRT_PATH.equals(SchematicBlocks.path(blockId));
    }

    public static boolean isDecorFlower(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестен декор сада");
        String path = SchematicBlocks.path(blockId);
        return FLOWERS.contains(path) || path.endsWith("_tulip");
    }

    public static boolean isNotBought(String blockId, Map<String, String> state) {
        return isActionPlaced(blockId) || isDecorFlower(blockId) || SchematicBlocks.isSecondaryPart(blockId, state);
    }

    public static boolean isTrampleGround(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестна почва под тропинку");
        String path = SchematicBlocks.path(blockId);
        return DIRT.equals(path)
                || GRASS.equals(path)
                || PODZOL.equals(path)
                || COARSE_DIRT.equals(path)
                || ROOTED_DIRT.equals(path)
                || MYCELIUM.equals(path);
    }

    public static String shovelItem(boolean cobblestone) {
        return cobblestone ? STONE_SHOVEL : WOODEN_SHOVEL;
    }
}
