package com.theminespirit.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.StrayRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class LiveFloatingWorld implements TreeView, FloatingBlocks.World {
    public static final Path FIXTURE = Path.of("src/test/resources/live/floattrees/floating-trees.json");
    private static final Set<String> NATURAL = Set.of(
            "minecraft:stone",
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:gravel",
            "minecraft:sand",
            "minecraft:granite",
            "minecraft:diorite",
            "minecraft:vine",
            "minecraft:short_grass",
            "minecraft:coal_ore",
            "minecraft:copper_ore");
    private static final Set<String> EMPTY =
            Set.of("minecraft:air", "minecraft:vine", "minecraft:short_grass", "minecraft:cave_air");

    private final Map<BlockCoord, String> blocks;
    private final JsonNode root;

    private LiveFloatingWorld(JsonNode root, Map<BlockCoord, String> blocks) {
        this.root = root;
        this.blocks = blocks;
    }

    public static LiveFloatingWorld load() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8));
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (JsonNode entry : root.get("blocks")) {
            blocks.put(
                    new BlockCoord(
                            entry.get("x").asInt(),
                            entry.get("y").asInt(),
                            entry.get("z").asInt()),
                    entry.get("block").asText());
        }
        return new LiveFloatingWorld(root, blocks);
    }

    public JsonNode root() {
        return root;
    }

    private String at(BlockCoord coord) {
        return blocks.getOrDefault(coord, "minecraft:air");
    }

    @Override
    public String blockId(BlockCoord at) {
        return at(at);
    }

    @Override
    public boolean natural(String blockId) {
        return NATURAL.contains(blockId) || blockId.endsWith("_leaves");
    }

    @Override
    public boolean planned(BlockCoord at) {
        return false;
    }

    @Override
    public boolean temporary(BlockCoord at) {
        return false;
    }

    @Override
    public boolean log(BlockCoord at) {
        return isLog(at);
    }

    @Override
    public boolean treeLog(BlockCoord at) {
        return !StrayRule.treeLogs(at, this).isEmpty();
    }

    @Override
    public boolean supported(BlockCoord at) {
        BlockCoord below = new BlockCoord(at.x(), at.y() - 1, at.z());
        return !EMPTY.contains(blockId(below));
    }

    @Override
    public boolean registeredChest(BlockCoord at) {
        return false;
    }

    @Override
    public int groundY(int x, int z) {
        for (int y = 100; y >= 60; y--) {
            BlockCoord at = new BlockCoord(x, y, z);
            if (!EMPTY.contains(blockId(at))) {
                return y;
            }
        }
        return 60;
    }

    @Override
    public boolean isLog(BlockCoord coord) {
        String block = at(coord);
        return block.endsWith("_log") || block.endsWith("_wood");
    }

    @Override
    public boolean isNaturalLeaves(BlockCoord coord) {
        return at(coord).endsWith("_leaves");
    }

    @Override
    public boolean isSoil(BlockCoord coord) {
        String block = at(coord);
        return "minecraft:dirt".equals(block) || "minecraft:grass_block".equals(block);
    }

    @Override
    public boolean isArtificial(BlockCoord coord) {
        String block = at(coord);
        return block.endsWith("_planks")
                || block.endsWith("_fence")
                || block.endsWith("_fence_gate")
                || block.endsWith("_torch")
                || block.endsWith("_stairs")
                || "minecraft:crafting_table".equals(block)
                || "minecraft:furnace".equals(block)
                || "minecraft:chest".equals(block);
    }

    @Override
    public boolean isEmpty(BlockCoord coord) {
        return EMPTY.contains(blockId(coord));
    }
}
