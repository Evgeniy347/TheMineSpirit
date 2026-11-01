package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class TreeSupport {
    private TreeSupport() {}

    public static boolean isolatedLog(BlockCoord at, Predicate<BlockCoord> isLog) {
        Objects.requireNonNull(at, "Клетка бревна не может быть null: без неё не видно соседей");
        Objects.requireNonNull(isLog, "Проверка бревна не может быть null: по ней ищется ствол");
        int around = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (isLog.test(new BlockCoord(at.x() + dx, at.y() + dy, at.z() + dz)) && ++around > 3) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<BlockCoord> candidates(List<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки дерева не могут быть null: без них не видно, где ставить опору");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("У дерева нет клеток: опору ставить не к чему");
        }
        int minX = cells.stream().mapToInt(BlockCoord::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(BlockCoord::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(BlockCoord::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(BlockCoord::z).max().orElseThrow();
        int lowY = cells.stream().mapToInt(BlockCoord::y).min().orElseThrow();
        int midX = minX + (maxX - minX) / 2;
        int midZ = minZ + (maxZ - minZ) / 2;
        int[][] around = {
            {minX - 1, minZ}, {maxX + 1, minZ}, {minX - 1, maxZ}, {maxX + 1, maxZ},
            {midX, minZ - 1}, {midX, maxZ + 1}, {minX - 1, midZ}, {maxX + 1, midZ}
        };
        List<BlockCoord> columns = new ArrayList<>(around.length);
        for (int[] column : around) {
            columns.add(new BlockCoord(column[0], lowY, column[1]));
        }
        return List.copyOf(columns);
    }
}
