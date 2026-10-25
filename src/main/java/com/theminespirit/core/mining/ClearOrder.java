package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ClearOrder {
    private ClearOrder() {}

    public static List<BlockCoord> topmost(List<BlockCoord> cells, Predicate<BlockCoord> solid) {
        Objects.requireNonNull(cells, "Клетки ящика не могут быть null: пустой ящик задают пустым списком");
        Objects.requireNonNull(solid, "Проверка блока не может быть null: без неё не видно, что ещё стоит");
        Set<BlockCoord> standing = new HashSet<>();
        for (BlockCoord cell : cells) {
            if (solid.test(cell)) {
                standing.add(cell);
            }
        }
        return cells.stream()
                .filter(standing::contains)
                .filter(cell -> !standing.contains(new BlockCoord(cell.x(), cell.y() + 1, cell.z())))
                .toList();
    }
}
