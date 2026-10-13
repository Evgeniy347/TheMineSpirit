package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FortifyBatcher {
    private FortifyBatcher() {
    }

    public static List<List<BlockCoord>> batches(List<BlockCoord> cells, int perTick) {
        Objects.requireNonNull(cells, "Порции без плана не режутся: без клеток неизвестно, что ставить за тик");
        if (perTick < 1) {
            throw new IllegalArgumentException("Порция постановки = " + perTick
                    + ": за тик ставится хотя бы один блок");
        }
        List<List<BlockCoord>> portions = new ArrayList<>();
        for (int from = 0; from < cells.size(); from += perTick) {
            portions.add(List.copyOf(cells.subList(from, Math.min(cells.size(), from + perTick))));
        }
        return List.copyOf(portions);
    }

    public static List<BlockCoord> selfBuried(List<BlockCoord> cells, Set<BlockCoord> occupiedBySpirit) {
        Objects.requireNonNull(cells, "Список ячеек не может быть null: без него неизвестно, что откладывать");
        Objects.requireNonNull(occupiedBySpirit,
                "Множество занятых духом ячеек не может быть null: пустое задают пустым множеством");
        return BuildOrder.deferred(cells, occupiedBySpirit);
    }
}
