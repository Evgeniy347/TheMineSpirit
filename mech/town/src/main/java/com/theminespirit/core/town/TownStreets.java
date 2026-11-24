package com.theminespirit.core.town;

import java.util.Objects;

public record TownStreets(ColumnMask main, ColumnMask side, ColumnMask ring) {
    public TownStreets {
        Objects.requireNonNull(main, "Главные улицы не могут быть null");
        Objects.requireNonNull(side, "Боковые улицы не могут быть null");
        Objects.requireNonNull(ring, "Кольцевая улица не может быть null");
    }

    public boolean contains(int x, int z) {
        return main.contains(x, z) || side.contains(x, z) || ring.contains(x, z);
    }
}
