package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record DescentStep(int index, int floor, BlockCoord stand, List<BlockCoord> passage) {
    public DescentStep {
        if (index < 0) {
            throw new IllegalArgumentException("Номер шага " + index + " отрицательный: шаги считаются от нуля");
        }
        Objects.requireNonNull(stand, "Клетка ступени не может быть null: дух встаёт именно на неё");
        Objects.requireNonNull(passage, "Проход шага не может быть null: без клеток непонятно, что копать");
        if (passage.size() != 3) {
            throw new IllegalArgumentException("Проход шага " + index + " состоит из " + passage.size()
                    + " клеток вместо трёх: в проходе дух стоит в полный рост");
        }
        if (floor != stand.y() - 1) {
            throw new IllegalArgumentException("Этаж " + floor + " шага " + index + " не пол под ступенью " + stand
                    + ": пол — ровно блок под ногами духа");
        }
        for (int offset = 0; offset < passage.size(); offset++) {
            BlockCoord cell = passage.get(offset);
            if (cell.y() != floor + 1 + offset) {
                throw new IllegalArgumentException("Клетка " + cell + " прохода шага " + index
                        + " не стоит на высоте floor+" + (offset + 1) + ": проход идёт ровно три блока вверх");
            }
        }
        passage = List.copyOf(passage);
    }

    public BlockCoord torch() {
        return stand;
    }
}
