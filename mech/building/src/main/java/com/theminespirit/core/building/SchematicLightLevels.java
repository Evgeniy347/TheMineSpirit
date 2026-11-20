package com.theminespirit.core.building;

import java.util.Map;
import java.util.Objects;

final class SchematicLightLevels {
    private SchematicLightLevels() {}

    private static final Map<String, Integer> LIGHT_LEVELS = Map.ofEntries(
            Map.entry(SchematicBlocks.TORCH, 14),
            Map.entry("wall_torch", 14),
            Map.entry("soul_torch", 10),
            Map.entry("soul_wall_torch", 10),
            Map.entry("redstone_torch", 7),
            Map.entry("redstone_wall_torch", 7),
            Map.entry(SchematicBlocks.LANTERN, 15),
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

    static int lightLevel(String blockId, Map<String, String> state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: пустое задаётся пустой картой");
        String path = SchematicBlocks.path(blockId);
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

    static boolean blocksLight(String blockId) {
        String path = SchematicBlocks.path(blockId);
        if (path.endsWith("_slab") || path.endsWith("_stairs")) {
            return true;
        }
        return SchematicBlocks.isFullBlock(blockId)
                && !SchematicBlocks.isWindow(blockId)
                && !SchematicBlocks.isChest(blockId)
                && !"enchanting_table".equals(path)
                && !path.endsWith("_leaves")
                && !path.endsWith("ice")
                && !LIGHT_LEVELS.containsKey(path);
    }
}
