package com.theminespirit.core.building;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Свойства блоков схем по id: полный блок (даёт опору), окно, дверь, проём, сундук, источник света.
 * Схема хранит только id блока, поэтому свойства решаются по Suffix-правилам, а не по состоянию.
 */
public final class SchematicBlocks {
    private static final Set<String> NON_FULL = Set.of(
            "air", "cave_air", "void_air", "water", "lava", "fire", "soul_fire",
            "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
            "lantern", "soul_lantern", "chain", "end_rod", "light", "iron_bars", "cobweb",
            "flower_pot", "brewing_stand", "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron",
            "campfire", "soul_campfire");

    private static final List<String> NON_FULL_SUFFIXES = List.of(
            "_fence", "_fence_gate", "_pane", "_slab", "_stairs", "_door", "_trapdoor", "_button",
            "_pressure_plate", "_carpet", "_bed", "_torch", "_sign", "_banner", "_sapling", "_flower",
            "_grass", "_fern", "_leaves", "_rail", "_vine", "_ladder", "_scaffolding", "_lantern",
            "_chain", "_candle", "_cactus", "_bamboo", "_kelp", "_seagrass", "_coral", "_wheat",
            "_carrots", "_potatoes", "_beetroots", "_stem", "_roots", "_sprouts", "_petals", "_bush",
            "_mushroom", "_fungus", "_sugar_cane", "_snow_layer", "_tulip", "_dandelion", "_poppy",
            "_orchid", "_allium", "_bluet", "_daisy", "_cornflower", "_lily", "_azalea", "_pickle",
            "_frogspawn", "_cocoa", "_wart", "_chorus_flower");

    private static final Set<String> LIGHT_SOURCES = Set.of(
            "torch", "wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern",
            "glowstone", "sea_lantern", "shroomlight", "jack_o_lantern", "campfire", "soul_campfire",
            "fire", "soul_fire", "beacon", "conduit", "end_rod", "magma_block", "lava");

    private SchematicBlocks() {
    }

    public static String path(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестны свойства блока");
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }

    public static boolean isFullBlock(String blockId) {
        String path = path(blockId);
        if (NON_FULL.contains(path)) {
            return false;
        }
        for (String suffix : NON_FULL_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWindow(String blockId) {
        String path = path(blockId);
        return path.equals("glass") || path.endsWith("_glass") || path.endsWith("_pane");
    }

    public static boolean isDoor(String blockId) {
        return path(blockId).endsWith("_door");
    }

    public static boolean isEntranceBlock(String blockId) {
        String path = path(blockId);
        return isDoor(path) || path.endsWith("_fence_gate") || path.endsWith("_trapdoor")
                || path.endsWith("_gate");
    }

    public static boolean isChest(String blockId) {
        String path = path(blockId);
        return path.equals("chest") || path.equals("trapped_chest");
    }

    public static boolean isLightSource(String blockId) {
        String path = path(blockId);
        return LIGHT_SOURCES.contains(path) || path.endsWith("_torch") || path.endsWith("_lantern")
                || path.endsWith("_candle");
    }
}
