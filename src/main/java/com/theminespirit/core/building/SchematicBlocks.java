package com.theminespirit.core.building;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Свойства блоков схем по id: полный блок (даёт опору), окно, дверь, кровать, проём, сундук, источник света,
 * настенный блок и предмет, которым блок ставится. Сторожа работают без игры, поэтому свойства решаются правилами
 * по имени блока, а половина двери и часть кровати — по состоянию.
 */
public final class SchematicBlocks {
    private static final Set<String> NON_FULL = Set.of(
            "air",
            "cave_air",
            "void_air",
            "water",
            "lava",
            "fire",
            "soul_fire",
            "torch",
            "wall_torch",
            "soul_torch",
            "soul_wall_torch",
            "redstone_torch",
            "redstone_wall_torch",
            "lantern",
            "soul_lantern",
            "chain",
            "end_rod",
            "light",
            "iron_bars",
            "cobweb",
            "flower_pot",
            "brewing_stand",
            "cauldron",
            "water_cauldron",
            "lava_cauldron",
            "powder_snow_cauldron",
            "campfire",
            "soul_campfire");

    private static final List<String> NON_FULL_SUFFIXES = List.of(
            "_fence",
            "_fence_gate",
            "_pane",
            "_slab",
            "_stairs",
            "_door",
            "_trapdoor",
            "_button",
            "_pressure_plate",
            "_carpet",
            "_bed",
            "_torch",
            "_sign",
            "_banner",
            "_sapling",
            "_flower",
            "_grass",
            "_fern",
            "_leaves",
            "_rail",
            "_vine",
            "_ladder",
            "_scaffolding",
            "_lantern",
            "_chain",
            "_candle",
            "_cactus",
            "_bamboo",
            "_kelp",
            "_seagrass",
            "_coral",
            "_wheat",
            "_carrots",
            "_potatoes",
            "_beetroots",
            "_stem",
            "_roots",
            "_sprouts",
            "_petals",
            "_bush",
            "_mushroom",
            "_fungus",
            "_sugar_cane",
            "_snow_layer",
            "_tulip",
            "_dandelion",
            "_poppy",
            "_orchid",
            "_allium",
            "_bluet",
            "_daisy",
            "_cornflower",
            "_lily",
            "_azalea",
            "_pickle",
            "_frogspawn",
            "_cocoa",
            "_wart",
            "_chorus_flower");

    private static final Set<String> LIGHT_SOURCES = Set.of(
            "torch",
            "wall_torch",
            "soul_torch",
            "soul_wall_torch",
            "lantern",
            "soul_lantern",
            "glowstone",
            "sea_lantern",
            "shroomlight",
            "jack_o_lantern",
            "campfire",
            "soul_campfire",
            "fire",
            "soul_fire",
            "beacon",
            "conduit",
            "end_rod",
            "magma_block",
            "lava");

    private static final String[][] WALL_VARIANTS = {
        {"wall_torch", "torch"},
        {"_wall_sign", "_sign"},
        {"_wall_hanging_sign", "_hanging_sign"},
        {"_wall_banner", "_banner"},
        {"_wall_head", "_head"},
        {"_wall_skull", "_skull"},
        {"_wall_fan", "_fan"}
    };

    private SchematicBlocks() {}

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
        return "glass".equals(path) || path.endsWith("_glass") || path.endsWith("_pane");
    }

    public static boolean isDoor(String blockId) {
        return path(blockId).endsWith("_door");
    }

    public static String itemOf(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестен предмет");
        int colon = blockId.indexOf(':');
        String namespace = colon < 0 ? "minecraft:" : blockId.substring(0, colon + 1);
        String path = path(blockId);
        for (String[] wall : WALL_VARIANTS) {
            if (path.endsWith(wall[0])) {
                return namespace + path.substring(0, path.length() - wall[0].length()) + wall[1];
            }
        }
        return namespace + path;
    }

    public static boolean isWallMounted(String blockId) {
        String path = path(blockId);
        for (String[] wall : WALL_VARIANTS) {
            if (path.endsWith(wall[0])) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBed(String blockId) {
        return path(blockId).endsWith("_bed");
    }

    public static boolean isSecondaryPart(String blockId, Map<String, String> state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: пустое задаётся пустой картой");
        if (isDoor(blockId)) {
            return "upper".equals(state.get("half"));
        }
        if (isBed(blockId)) {
            return "head".equals(state.get("part"));
        }
        return false;
    }

    public static boolean isEntranceBlock(String blockId) {
        String path = path(blockId);
        return isDoor(path) || path.endsWith("_fence_gate") || path.endsWith("_trapdoor") || path.endsWith("_gate");
    }

    public static boolean isChest(String blockId) {
        String path = path(blockId);
        return "chest".equals(path) || "trapped_chest".equals(path);
    }

    public static boolean isLightSource(String blockId) {
        String path = path(blockId);
        return LIGHT_SOURCES.contains(path)
                || path.endsWith("_torch")
                || path.endsWith("_lantern")
                || path.endsWith("_candle");
    }
}
