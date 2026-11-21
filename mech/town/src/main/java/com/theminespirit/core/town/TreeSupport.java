package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TreeSupport {
    private TreeSupport() {}

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
        int midX = (int) (((long) minX + maxX) / 2);
        int midZ = (int) (((long) minZ + maxZ) / 2);
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
