package com.theminespirit.core.town;

import java.util.List;
import java.util.Objects;

public record PathLine(String id, List<ColumnMask.Cell> cells) {
    public PathLine {
        Objects.requireNonNull(id, "id тропинки не может быть null: по нему её правят");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id тропинки пуст: без него правки не находят линию");
        }
        cells = List.copyOf(Objects.requireNonNull(cells, "клетки тропинки не могут быть null"));
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("тропинка '" + id + "' без клеток: строить нечего");
        }
    }
}
