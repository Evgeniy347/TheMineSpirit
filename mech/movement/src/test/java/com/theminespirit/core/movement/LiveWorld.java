package com.theminespirit.core.movement;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class LiveWorld implements RouteWorld, NavWorld {
    private final Map<BlockCoord, BlockKind> cells = new HashMap<>();

    static LiveWorld load(String resource) throws IOException {
        LiveWorld world = new LiveWorld();
        world.cells.putAll(LiveFixture.load(resource));
        return world;
    }

    private BlockKind kind(int x, int y, int z) {
        return cells.getOrDefault(new BlockCoord(x, y, z), BlockKind.HARD);
    }

    @Override
    public int cell(int x, int y, int z) {
        return switch (kind(x, y, z)) {
            case AIR -> RouteCell.AIR;
            case WATER -> RouteCell.WATER;
            case LAVA -> RouteCell.LAVA;
            case SOFT, FALLING -> RouteCell.solid(8);
            case HARD -> RouteCell.solid(20);
            case BEDROCK, OBSTACLE -> RouteCell.BLOCKED;
        };
    }

    @Override
    public boolean available(int x, int z) {
        return true;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        BlockKind value = kind(x, y, z);
        return value == BlockKind.AIR || value == BlockKind.WATER;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        BlockKind value = kind(x, y, z);
        return value == BlockKind.SOFT
                || value == BlockKind.HARD
                || value == BlockKind.FALLING
                || value == BlockKind.BEDROCK;
    }

    @Override
    public boolean water(int x, int y, int z) {
        return kind(x, y, z) == BlockKind.WATER;
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return false;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        return kind(x, y, z) == BlockKind.BEDROCK ? Double.POSITIVE_INFINITY : 10;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 320;
    }
}
