package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;

public record FarmPlot(int minX, int minZ, int maxX, int maxZ) {
    public FarmPlot {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "Минимум грядки больше максимума: углы обязаны нормализоваться через of(...), "
                            + "а не конструктором");
        }
        int sideX = maxX - minX + 1;
        int sideZ = maxZ - minZ + 1;
        if (sideX > CatalogLimits.FARM_PLOT_SIDE_MAX || sideZ > CatalogLimits.FARM_PLOT_SIDE_MAX) {
            throw new IllegalArgumentException("Сторона грядки " + sideX + "×" + sideZ + " больше предела "
                    + CatalogLimits.FARM_PLOT_SIDE_MAX + ": разбейте грядку на части");
        }
    }

    public static FarmPlot of(int x1, int z1, int x2, int z2) {
        return new FarmPlot(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public int sideX() {
        return maxX - minX + 1;
    }

    public int sideZ() {
        return maxZ - minZ + 1;
    }

    public int cells() {
        return sideX() * sideZ();
    }

    public List<BlockCoord> grounds(int groundY) {
        List<BlockCoord> grounds = new ArrayList<>(cells());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                grounds.add(new BlockCoord(x, groundY, z));
            }
        }
        return List.copyOf(grounds);
    }

    public BlockCoord center(int groundY) {
        return new BlockCoord((minX + maxX) / 2, groundY, (minZ + maxZ) / 2);
    }

    public int searchRadius(int groundY) {
        BlockCoord center = center(groundY);
        int farthest = 0;
        for (BlockCoord corner : List.of(
                new BlockCoord(minX, groundY, minZ),
                new BlockCoord(minX, groundY, maxZ),
                new BlockCoord(maxX, groundY, minZ),
                new BlockCoord(maxX, groundY, maxZ))) {
            int distance = Math.max(Math.abs(corner.x() - center.x()), Math.abs(corner.z() - center.z()));
            farthest = Math.max(farthest, distance);
        }
        return farthest;
    }
}
