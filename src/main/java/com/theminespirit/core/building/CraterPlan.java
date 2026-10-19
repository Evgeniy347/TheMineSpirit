package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * План засыпки воронки от взрыва: клетки-пустоты ниже уровня окружающей земли, снизу вверх.
 *
 * <p>Верх засыпки — ровно уровень земли столбца, поэтому после работы ямы не остаётся. Воду, лаву и
 * защищённые (чужие) клетки план пропускает: их не засыпают.
 */
public final class CraterPlan {
    private CraterPlan() {}

    public record Plan(List<BlockCoord> cells, int blockCount) {
        public Plan {
            cells = List.copyOf(
                    Objects.requireNonNull(cells, "cells не может быть null: пустой план задают пустым списком"));
            if (blockCount != cells.size()) {
                throw new IllegalArgumentException("blockCount " + blockCount + " не равен числу клеток " + cells.size()
                        + ": расход блоков обязан совпадать с планом");
            }
        }
    }

    public static Plan plan(CraterSurface surface) {
        Objects.requireNonNull(surface, "surface не может быть null: без неё неизвестны высоты краёв");
        BlockCoord center = surface.center();
        int radius = surface.radius();
        List<BlockCoord> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                collectColumn(surface, center.x() + dx, center.z() + dz, cells);
            }
        }
        cells.sort(Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return new Plan(cells, cells.size());
    }

    private static void collectColumn(CraterSurface surface, int x, int z, List<BlockCoord> cells) {
        int level = surface.groundLevel(x, z);
        if (!surface.isAir(x, level, z)) {
            return;
        }
        int floor = level;
        while (floor > CatalogLimits.WORLD_Y_MIN && surface.isAir(x, floor - 1, z)) {
            floor--;
        }
        for (int y = floor; y <= level; y++) {
            if (surface.isAir(x, y, z) && !surface.isProtected(x, y, z)) {
                cells.add(new BlockCoord(x, y, z));
            }
        }
    }
}
