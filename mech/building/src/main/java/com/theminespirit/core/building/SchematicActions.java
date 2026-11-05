package com.theminespirit.core.building;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
    private static final Map<String, String> ITEM_OVERRIDES = Map.of(
            "farmland", "dirt",
            "water", "water_bucket",
            "wheat", "wheat_seeds",
            "carrots", "carrot",
            "potatoes", "potato",
            "beetroots", "beetroot_seeds");

    private static final String DIRT_PATH = "dirt_path";
    private static final String FARMLAND = "farmland";
    private static final String WATER = "water";
    private static final String DIRT = "dirt";
    private static final String GRASS = "grass_block";
    private static final String PODZOL = "podzol";
    private static final String COARSE_DIRT = "coarse_dirt";
    private static final String ROOTED_DIRT = "rooted_dirt";
    private static final String MYCELIUM = "mycelium";
    private static final String WOODEN_SHOVEL = "minecraft:wooden_shovel";
    private static final String STONE_SHOVEL = "minecraft:stone_shovel";

    private SchematicActions() {}

    public static boolean buildEntry(String blockId, boolean hasItem) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, входит ли он в стройку");
        return hasItem || isActionPlaced(blockId) || isFluidPlaced(blockId);
    }

    public static boolean isActionPlaced(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, ставится ли он действием");
        String path = SchematicBlocks.path(blockId);
        return DIRT_PATH.equals(path) || FARMLAND.equals(path);
    }

    public static @Nullable String itemOverride(String path) {
        return ITEM_OVERRIDES.get(path);
    }

    public static boolean isTilled(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, пашут ли его тяпкой");
        return FARMLAND.equals(SchematicBlocks.path(blockId));
    }

    public static boolean isWalkway(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, обход ли это");
        return DIRT_PATH.equals(SchematicBlocks.path(blockId));
    }

    public static boolean isFluidPlaced(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, льётся ли он ведром");
        return WATER.equals(SchematicBlocks.path(blockId));
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
