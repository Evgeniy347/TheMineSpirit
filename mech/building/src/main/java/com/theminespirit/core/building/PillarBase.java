package com.theminespirit.core.building;

import java.util.Objects;

public final class PillarBase {
    public interface Column {
        boolean supported(int y);
    }

    private PillarBase() {}

    public static int first(int baseY, int minY, int maxDrop, Column column) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, где опора");
        if (maxDrop < 0) {
            throw new IllegalArgumentException("Спуск до опоры " + maxDrop + " отрицателен: опора только ниже ног");
        }
        for (int drop = 0; drop <= maxDrop; drop++) {
            int y = baseY - drop;
            if (y < minY) {
                return baseY;
            }
            if (column.supported(y)) {
                return y;
            }
        }
        return baseY;
    }
}
