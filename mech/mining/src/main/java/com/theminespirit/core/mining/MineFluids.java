package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class MineFluids {
    private static final int[][] FACES = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private MineFluids() {}

    public static List<BlockCoord> plugsBefore(BlockCoord cell, Set<BlockCoord> open, Predicate<BlockCoord> fluid) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что вскрывается");
        Objects.requireNonNull(open, "Открытый проход не может быть null: в начале — пустое множество");
        Objects.requireNonNull(fluid, "Проверка жидкости не может быть null: без неё дух копает в воду");
        List<BlockCoord> plugs = new ArrayList<>(FACES.length + 1);
        if (fluid.test(cell)) {
            plugs.add(cell);
        }
        for (int[] face : FACES) {
            BlockCoord neighbour = new BlockCoord(cell.x() + face[0], cell.y() + face[1], cell.z() + face[2]);
            if (!open.contains(neighbour) && fluid.test(neighbour)) {
                plugs.add(neighbour);
            }
        }
        return List.copyOf(plugs);
    }
}
