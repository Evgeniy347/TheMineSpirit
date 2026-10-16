package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;

/** Снимок запасов поселения: сколько каких предметов есть в наличии (инвентари духов + контейнеры). */
public record StockView(Map<String, Integer> available) {

    public StockView {
        Objects.requireNonNull(available, "available не может быть null: пустой снимок запасов "
                + "задают пустой картой, а не null");
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Запас предмета '" + entry.getKey() + "' равен "
                        + entry.getValue() + ": количество не может быть отрицательным или null");
            }
        }
        available = Map.copyOf(available);
    }
}
