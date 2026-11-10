package com.theminespirit.core.navigation;

import com.theminespirit.core.movement.BlockKind;
import com.theminespirit.core.movement.LiveFixture;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Map;

final class LiveNavWorld implements NavWorld {
    private final Map<BlockCoord, BlockKind> cells;

    private LiveNavWorld(Map<BlockCoord, BlockKind> cells) {
        this.cells = cells;
    }

    static LiveNavWorld live() throws IOException {
        return live("/live/pit-warehouse-0611.live");
    }

    static LiveNavWorld live(String resource) throws IOException {
        return new LiveNavWorld(LiveFixture.load(resource));
    }

    private BlockKind kind(int x, int y, int z) {
        return cells.getOrDefault(new BlockCoord(x, y, z), BlockKind.HARD);
    }

    @Override
    public boolean available(int x, int z) {
        return true;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        BlockKind kind = kind(x, y, z);
        return kind == BlockKind.AIR || kind == BlockKind.WATER;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        BlockKind kind = kind(x, y, z);
        return kind == BlockKind.SOFT
                || kind == BlockKind.HARD
                || kind == BlockKind.FALLING
                || kind == BlockKind.BEDROCK;
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
        BlockKind kind = kind(x, y, z);
        if (kind == BlockKind.AIR || kind == BlockKind.WATER) {
            return 0;
        }
        return kind == BlockKind.BEDROCK ? NavActionCosts.COST_INF : 10;
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
