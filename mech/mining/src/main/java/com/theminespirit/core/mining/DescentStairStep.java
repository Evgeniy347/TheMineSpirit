package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DescentStairStep(int index, int floor, List<BlockCoord> stairCells, List<BlockCoord> railCells) {
    public DescentStairStep {
        if (index < 0) {
            throw new IllegalArgumentException("Номер шага " + index + " отрицательный: шаги считаются от нуля");
        }
        Objects.requireNonNull(stairCells, "Клетки ступенек не могут быть null: без них непонятно, где ступеньки");
        Objects.requireNonNull(railCells, "Клетки рельсового прохода не могут быть null: пустой задают пустым списком");
        if (stairCells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Шаг " + index + " без единой ступеньки: ступеньки ставят на каждом шаге");
        }
        List<BlockCoord> combined = new ArrayList<>(stairCells);
        combined.addAll(railCells);
        for (BlockCoord cell : combined) {
            if (cell.y() != floor + 1) {
                throw new IllegalArgumentException("Клетка " + cell + " шага " + index + " не на высоте floor+1: "
                        + "ступенька ставится в нижнюю клетку прохода, на пол");
            }
        }
        Set<BlockCoord> unique = new HashSet<>(combined);
        if (unique.size() != combined.size()) {
            throw new IllegalArgumentException("Ступеньки и рельсовый проход шага " + index
                    + " пересекаются: клетка не может быть и ступенькой, и рельсом");
        }
        stairCells = List.copyOf(stairCells);
        railCells = List.copyOf(railCells);
    }
}
