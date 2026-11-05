package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AreaReplant {
    private AreaReplant() {}

    public static List<BlockCoord> plan(
            BlockCoord center, Collection<BlockCoord> excluded, ReplantData data, TreeView view) {
        Objects.requireNonNull(center, "center не может быть null: сетка досадки отсчитывается от основания дерева");
        Objects.requireNonNull(excluded, "excluded не может быть null: клетки ствола исключаются из досадки");
        Objects.requireNonNull(data, "data не может быть null: шаг, радиус и зазор приходят данными");
        Objects.requireNonNull(view, "view не может быть null: без чтения мира неизвестно, где почва и постройки");
        Set<BlockCoord> skip = new LinkedHashSet<>(excluded);
        int radius = data.areaRadius();
        int step = data.areaStep();
        List<BlockCoord> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockCoord cell = new BlockCoord(center.x() + dx, center.y(), center.z() + dz);
                if (skip.contains(cell) || !view.isEmpty(cell)) {
                    continue;
                }
                if (!view.isSoil(new BlockCoord(cell.x(), cell.y() - 1, cell.z()))) {
                    continue;
                }
                if (nearBuilding(cell, data.buildingGap(), view)) {
                    continue;
                }
                cells.add(cell);
            }
        }
        cells.sort(Comparator.comparingInt((BlockCoord cell) -> distanceSquared(center, cell))
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return List.copyOf(cells);
    }

    static boolean nearBuilding(BlockCoord cell, int gap, TreeView view) {
        int reach = gap - 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (view.isArtificial(new BlockCoord(cell.x() + dx, cell.y() + dy, cell.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int distanceSquared(BlockCoord first, BlockCoord second) {
        int dx = first.x() - second.x();
        int dz = first.z() - second.z();
        return dx * dx + dz * dz;
    }
}
