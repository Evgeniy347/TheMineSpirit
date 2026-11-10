package com.theminespirit.core.navigation;

import com.theminespirit.core.movement.LiveFixture;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Map;

final class LiveNavWorld implements NavWorld {
    private enum Kind {
        AIR,
        SOFT,
        HARD,
        FALLING,
        WATER,
        LAVA,
        BEDROCK,
        OBSTACLE
    }

    private final Map<BlockCoord, Kind> cells;

    private LiveNavWorld(Map<BlockCoord, Kind> cells) {
        this.cells = cells;
    }

    static LiveNavWorld live() throws IOException {
        return live("/live/pit-warehouse-0611.live");
    }

    static LiveNavWorld live(String resource) throws IOException {
        return new LiveNavWorld(LiveFixture.load(Kind.class, resource));
    }

    private Kind kind(int x, int y, int z) {
        return cells.getOrDefault(new BlockCoord(x, y, z), Kind.HARD);
    }

    @Override
    public boolean available(int x, int z) {
        return true;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.AIR || kind == Kind.WATER;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.SOFT || kind == Kind.HARD || kind == Kind.FALLING || kind == Kind.BEDROCK;
    }

    @Override
    public boolean water(int x, int y, int z) {
        return kind(x, y, z) == Kind.WATER;
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return false;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        if (kind == Kind.AIR || kind == Kind.WATER) {
            return 0;
        }
        return kind == Kind.BEDROCK ? NavActionCosts.COST_INF : 10;
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
