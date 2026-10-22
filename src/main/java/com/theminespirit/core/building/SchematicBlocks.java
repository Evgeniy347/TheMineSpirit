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
    public static final String FLOOR = "@floor";

    private static final String FLOOR_BLOCK = "minecraft:cobblestone";
    private static final Set<String> FLOOR_CLASS = Set.of(
            "stone",
            "cobblestone",
            "mossy_cobblestone",
            "dirt",
            "coarse_dirt",
            "rooted_dirt",
            "deepslate",
            "cobbled_deepslate",
            "tuff",
            "andesite",
            "diorite",
            "granite",
            "stone_bricks",
            "mossy_stone_bricks",
            "calcite",
            "dripstone_block",
            "gravel",
            "sand",
            "red_sand");

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
            "soul_campfire",
            "ladder",
            "bell",
            "lectern",
            "poppy",
            "dandelion",
            "cornflower",
            "allium",
            "azure_bluet",
            "oxeye_daisy",
            "blue_orchid",
            "lily_of_the_valley",
            "azalea",
            "snow");

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

    private static final Map<String, Integer> LIGHT_LEVELS = Map.ofEntries(
            Map.entry("torch", 14),
            Map.entry("wall_torch", 14),
            Map.entry("soul_torch", 10),
            Map.entry("soul_wall_torch", 10),
            Map.entry("redstone_torch", 7),
            Map.entry("redstone_wall_torch", 7),
            Map.entry("lantern", 15),
            Map.entry("soul_lantern", 10),
            Map.entry("glowstone", 15),
            Map.entry("sea_lantern", 15),
            Map.entry("shroomlight", 15),
            Map.entry("jack_o_lantern", 15),
            Map.entry("campfire", 15),
            Map.entry("soul_campfire", 10),
            Map.entry("fire", 15),
            Map.entry("soul_fire", 10),
            Map.entry("beacon", 15),
            Map.entry("conduit", 15),
            Map.entry("end_rod", 14),
            Map.entry("magma_block", 3),
            Map.entry("lava", 15));

    private static final int CANDLE_LIGHT = 3;

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

    public static boolean isFloor(String blockId) {
        return FLOOR.equals(blockId);
    }

    // Клетка @floor принимает любой дешёвый твёрдый блок, а не только тот, что записан в легенде.
    public static boolean isFloorClass(String blockId) {
        return FLOOR.equals(blockId) || FLOOR_CLASS.contains(path(blockId));
    }

    // Материал, которым @floor ставится, когда в клетке нет подходящего блока.
    public static String placementBlock(String blockId) {
        return FLOOR.equals(blockId) ? FLOOR_BLOCK : blockId;
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
        String path = path(placementBlock(blockId));
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

    public static int lightLevel(String blockId, Map<String, String> state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: пустое задаётся пустой картой");
        String path = path(blockId);
        if ("false".equals(state.get("lit"))) {
            return 0;
        }
        if (path.endsWith("_candle") || "candle".equals(path)) {
            return "true".equals(state.get("lit"))
                    ? CANDLE_LIGHT * Integer.parseInt(state.getOrDefault("candles", "1"))
                    : 0;
        }
        return LIGHT_LEVELS.getOrDefault(path, 0);
    }

    public static boolean blocksLight(String blockId) {
        String path = path(blockId);
        if (path.endsWith("_slab") || path.endsWith("_stairs")) {
            return true;
        }
        return isFullBlock(blockId)
                && !isWindow(blockId)
                && !isChest(blockId)
                && !"enchanting_table".equals(path)
                && !path.endsWith("_leaves")
                && !path.endsWith("ice")
                && !LIGHT_LEVELS.containsKey(path);
    }

    public static boolean isSpawnSurface(String blockId) {
        String path = path(blockId);
        return isFullBlock(blockId)
                && !isWindow(blockId)
                && !isChest(blockId)
                && !"enchanting_table".equals(path)
                && !path.endsWith("_leaves");
    }

    public static boolean isHanging(String blockId, Map<String, String> state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: пустое задаётся пустой картой");
        String path = path(blockId);
        if ("chain".equals(path)) {
            return "y".equals(state.getOrDefault("axis", "y"));
        }
        return path.endsWith("lantern") && "true".equals(state.get("hanging"));
    }

    public static boolean holdsHanging(String blockId) {
        String path = path(blockId);
        return isFullBlock(blockId)
                || "chain".equals(path)
                || path.endsWith("_fence")
                || path.endsWith("_wall")
                || path.endsWith("_slab")
                || path.endsWith("_stairs");
    }

    public static boolean isCenterPost(String blockId) {
        String path = path(blockId);
        return path.endsWith("_fence") || path.endsWith("_wall");
    }

    public static boolean standsOnCenter(String blockId) {
        String path = path(blockId);
        return path.endsWith("_pressure_plate")
                || path.endsWith("lantern")
                || path.endsWith("candle")
                || "torch".equals(path)
                || "flower_pot".equals(path)
                || path.startsWith("potted_");
    }
}
