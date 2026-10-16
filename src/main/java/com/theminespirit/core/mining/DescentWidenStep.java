package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record DescentWidenStep(int index, int floor, int width, int height, BlockCoord stand,
        List<BlockCoord> cells) {
    public DescentWidenStep {
        if (index < 0) {
            throw new IllegalArgumentException("Номер шага " + index + " отрицательный: шаги считаются от нуля");
        }
        if (width < 1 || width % 2 == 0) {
            throw new IllegalArgumentException("Ширина расширения " + width
                    + " обязана быть нечётной и положительной: проход симметричен центральной колонке спуска");
        }
        if (height < 1) {
            throw new IllegalArgumentException("Высота расширения " + height + " меньше единицы: расширять нечем");
        }
        Objects.requireNonNull(stand, "Клетка ступени не может быть null: дух встаёт именно на неё");
        Objects.requireNonNull(cells, "Клетки расширения не могут быть null: без них непонятно, что копать");
        if (floor != stand.y() - 1) {
            throw new IllegalArgumentException("Этаж " + floor + " шага " + index + " не пол под ступенью " + stand
                    + ": пол — ровно блок под ногами духа");
        }
        if (cells.size() != width * height) {
            throw new IllegalArgumentException("Клеток расширения шага " + index + " — " + cells.size()
                    + " вместо " + (width * height) + ": проход шириной " + width + " и высотой " + height);
        }
        for (BlockCoord cell : cells) {
            if (cell.y() <= floor || cell.y() > floor + height) {
                throw new IllegalArgumentException("Клетка " + cell + " расширения шага " + index
                        + " не попадает в высоты floor+1..floor+" + height
                        + ": расширение обязано подниматься над полом в пределах высоты");
            }
        }
        cells = List.copyOf(cells);
    }

    public BlockCoord torch() {
        return stand;
    }
}
