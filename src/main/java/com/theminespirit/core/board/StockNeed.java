package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;

/** Нужда задания: сколько каких предметов требуется, чтобы задание могло взяться в работу. */
public record StockNeed(Map<String, Integer> items) {

    public StockNeed {
        Objects.requireNonNull(items, "items не может быть null: задание без ресурсов "
                + "задают пустой картой, а не null");
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Нужда в предмете '" + entry.getKey() + "' равна "
                        + entry.getValue() + ": количество не может быть отрицательным или null");
            }
        }
        items = Map.copyOf(items);
    }
}
