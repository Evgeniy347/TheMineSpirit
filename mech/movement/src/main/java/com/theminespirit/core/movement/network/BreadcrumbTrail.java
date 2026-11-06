package com.theminespirit.core.movement.network;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class BreadcrumbTrail {
    private final int keep;
    private final Deque<BlockCoord> cells = new ArrayDeque<>();

    public BreadcrumbTrail(int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException(
                    "Длина следа " + keep + " не положительна: обратный маршрут не из чего построить");
        }
        this.keep = keep;
    }

    public void remember(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка следа не может быть null: без неё не запомнить, где прошёл дух");
        if (!cells.isEmpty() && cells.peekLast().equals(cell)) {
            return;
        }
        cells.addLast(cell);
        while (cells.size() > keep) {
            cells.removeFirst();
        }
    }

    public List<BlockCoord> forward() {
        return List.copyOf(cells);
    }

    public List<BlockCoord> backtrack() {
        List<BlockCoord> reversed = new ArrayList<>(cells);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    public int size() {
        return cells.size();
    }
}
