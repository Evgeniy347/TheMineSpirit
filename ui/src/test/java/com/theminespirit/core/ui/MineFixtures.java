package com.theminespirit.core.ui;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MineFixtures {
    public static final int STEPS = 60;
    public static final int TUNNEL_SIZE = 7;
    private static final String STONE = "minecraft:stone";
    private static final String AIR = "minecraft:air";

    private MineFixtures() {}

    public static MineLayout layout() {
        List<BlockCoord> floors = new ArrayList<>();
        List<BlockCoord> air = new ArrayList<>();
        for (int step = 0; step < STEPS; step++) {
            for (int z = 0; z < 3; z++) {
                floors.add(new BlockCoord(step, 60 - step, z));
            }
            air.add(new BlockCoord(step, 61 - step, 1));
        }
        return new MineLayout(floors, air, List.of(), air.get(0));
    }

    public static MineGeometry geometry() {
        return MineGeometry.of(layout(), TUNNEL_SIZE);
    }

    public static List<MapMarker> markers() {
        return List.of(
                new MapMarker(MapMarker.Kind.SPIRIT, "spirit-1", "theminespirit.board.case.build_mine", 61, 4),
                new MapMarker(MapMarker.Kind.PLAYER, "Steve", "", 63, 6));
    }

    public static MineSnapshot snapshot(int y) {
        return MineSnapshot.of(geometry(), y, MineFixtures::at);
    }

    public static MineBlocks.Observed at(BlockCoord cell) {
        Map<BlockCoord, MineBlocks.Observed> world = world();
        return world.getOrDefault(cell, new MineBlocks.Observed(STONE, false));
    }

    private static Map<BlockCoord, MineBlocks.Observed> world() {
        MineGeometry geometry = geometry();
        Map<BlockCoord, MineBlocks.Observed> world = new HashMap<>();
        for (BlockCoord floor : geometry.floors()) {
            world.put(floor, new MineBlocks.Observed(STONE, false));
        }
        for (BlockCoord cell : geometry.air()) {
            world.put(cell, new MineBlocks.Observed(AIR, true));
        }
        for (BlockCoord cell : geometry.tunnel().cells()) {
            world.put(cell, new MineBlocks.Observed(AIR, true));
        }
        world.put(new BlockCoord(62, 2, 4), new MineBlocks.Observed("minecraft:coal_ore", false));
        world.put(new BlockCoord(63, 2, 5), new MineBlocks.Observed("minecraft:diamond_ore", false));
        world.put(new BlockCoord(60, 2, 6), new MineBlocks.Observed("minecraft:water", false));
        world.put(new BlockCoord(64, 2, 3), new MineBlocks.Observed("minecraft:lava", false));
        return world;
    }
}
