package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BuildOrder {
    private BuildOrder() {
    }

    public static List<BlockCoord> placement(List<BlockCoord> cells, Set<BlockCoord> occupiedBySpirit) {
        Objects.requireNonNull(cells, "Список ячеек не может быть null: без него неизвестно, что ставить");
        Objects.requireNonNull(occupiedBySpirit, "Множество занятых духом ячеек не может быть null: пустое задают пустым множеством");
        List<BlockCoord> bottomUp = new ArrayList<>(cells);
        bottomUp.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        List<BlockCoord> free = new ArrayList<>();
        List<BlockCoord> busy = new ArrayList<>();
        for (BlockCoord cell : bottomUp) {
            if (occupiedBySpirit.contains(cell)) {
                busy.add(cell);
            } else {
                free.add(cell);
            }
        }
        free.addAll(busy);
        return List.copyOf(free);
    }

    public static List<BlockCoord> deferred(List<BlockCoord> cells, Set<BlockCoord> occupiedBySpirit) {
        Objects.requireNonNull(cells, "Список ячеек не может быть null: без него неизвестно, что откладывать");
        Objects.requireNonNull(occupiedBySpirit, "Множество занятых духом ячеек не может быть null: пустое задают пустым множеством");
        return List.copyOf(cells.stream().filter(occupiedBySpirit::contains).toList());
    }
}
