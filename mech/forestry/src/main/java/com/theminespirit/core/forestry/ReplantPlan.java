package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReplantPlan(List<BlockCoord> cells, BlockCoord base, boolean square, boolean lacked) {
    public ReplantPlan {
        Objects.requireNonNull(cells, "cells не может быть null: пустой список — законный итог «саженцев нет»");
        Objects.requireNonNull(base, "base не может быть null: от основания отсчитывается обновление участка");
        cells = List.copyOf(cells);
    }

    public static ReplantPlan of(List<BlockCoord> trunkCells, int availableSaplings, ReplantData data) {
        Objects.requireNonNull(trunkCells, "trunkCells не может быть null: без клеток ствола сажать некуда");
        Objects.requireNonNull(data, "data не может быть null: правило пересадки приходит данными");
        if (trunkCells.isEmpty()) {
            throw new IllegalArgumentException("Нет клеток ствола: дерево без брёвен основания не пересаживают");
        }
        if (availableSaplings < 0) {
            throw new IllegalArgumentException(
                    "availableSaplings=" + availableSaplings + ": число саженцев не может быть отрицательным");
        }
        List<BlockCoord> sorted = trunkCells.stream().sorted().toList();
        BlockCoord base = sorted.get(0);
        boolean square = isFullSquare(sorted);
        if (square && availableSaplings >= data.squareSaplings()) {
            return new ReplantPlan(sorted, base, true, false);
        }
        if (availableSaplings >= 1) {
            return new ReplantPlan(List.of(base), base, square, square);
        }
        return new ReplantPlan(List.of(), base, square, true);
    }

    static boolean isFullSquare(List<BlockCoord> cells) {
        if (cells.size() != 4) {
            return false;
        }
        int y = cells.get(0).y();
        Set<BlockCoord> unique = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockCoord cell : cells) {
            if (cell.y() != y) {
                return false;
            }
            unique.add(cell);
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        return unique.size() == 4 && maxX - minX == 1 && maxZ - minZ == 1;
    }
}
