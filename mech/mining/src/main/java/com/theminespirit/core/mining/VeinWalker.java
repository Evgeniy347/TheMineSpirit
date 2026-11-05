package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VeinWalker {
    private static final int[][] NEIGHBORS = buildNeighbors();

    private VeinWalker() {}

    public static Vein collect(BlockCoord start, OreView view, int maxBlocks) {
        Objects.requireNonNull(start, "Начало обхода не может быть null: без него неизвестно, какую жилу искать");
        Objects.requireNonNull(view, "OreView не может быть null: по нему проверяется, руда ли клетка");
        if (maxBlocks < 1) {
            throw new IllegalArgumentException(
                    "Предел блоков жилы " + maxBlocks + " меньше 1: иначе обход " + "не вернёт ни одного блока");
        }
        Set<BlockCoord> visited = new HashSet<>();
        visited.add(start);
        if (!view.isOre(start)) {
            return new Vein(List.of(), false);
        }
        List<BlockCoord> blocks = new ArrayList<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        queue.add(start);
        boolean truncated = false;
        while (!queue.isEmpty()) {
            BlockCoord current = queue.poll();
            if (blocks.size() >= maxBlocks) {
                truncated = true;
                break;
            }
            blocks.add(current);
            for (int[] offset : NEIGHBORS) {
                BlockCoord neighbor =
                        new BlockCoord(current.x() + offset[0], current.y() + offset[1], current.z() + offset[2]);
                if (visited.add(neighbor) && view.isOre(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return new Vein(blocks, truncated);
    }

    private static int[][] buildNeighbors() {
        List<int[]> offsets = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }
}
