package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TestCraterSurface implements CraterSurface {
    private final BlockCoord center;
    private final int radius;
    private final Map<Long, Integer> levels = new HashMap<>();
    private final Map<Long, Integer> depths = new HashMap<>();
    private final Set<BlockCoord> solid = new HashSet<>();
    private final Set<BlockCoord> air = new HashSet<>();
    private final Set<BlockCoord> fluid = new HashSet<>();
    private final Set<BlockCoord> protectedCells = new HashSet<>();

    TestCraterSurface(BlockCoord center, int radius) {
        this.center = center;
        this.radius = radius;
    }

    TestCraterSurface level(int x, int z, int y) {
        levels.put(key(x, z), y);
        return this;
    }

    TestCraterSurface hole(int x, int z, int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException(
                    "Глубина ямы " + depth + " меньше 1: выкопанная яма минимум в одну клетку");
        }
        depths.put(key(x, z), depth);
        return this;
    }

    TestCraterSurface solid(BlockCoord pos) {
        solid.add(pos);
        return this;
    }

    TestCraterSurface air(BlockCoord pos) {
        air.add(pos);
        return this;
    }

    TestCraterSurface fluid(BlockCoord pos) {
        fluid.add(pos);
        return this;
    }

    TestCraterSurface protect(BlockCoord pos) {
        protectedCells.add(pos);
        return this;
    }

    @Override
    public BlockCoord center() {
        return center;
    }

    @Override
    public int radius() {
        return radius;
    }

    @Override
    public int groundLevel(int x, int z) {
        Integer level = levels.get(key(x, z));
        if (level == null) {
            throw new IllegalStateException("Нет уровня земли для столбца (" + x + ", " + z
                    + "): тест обязан задать уровень каждой клетке диска");
        }
        return level;
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        BlockCoord cell = new BlockCoord(x, y, z);
        if (air.contains(cell)) {
            return true;
        }
        if (solid.contains(cell)) {
            return false;
        }
        int level = groundLevel(x, z);
        int depth = depths.getOrDefault(key(x, z), 0);
        return y > level - depth;
    }

    @Override
    public boolean isFluid(int x, int y, int z) {
        return fluid.contains(new BlockCoord(x, y, z));
    }

    @Override
    public boolean isProtected(int x, int y, int z) {
        return protectedCells.contains(new BlockCoord(x, y, z));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
