package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record StockParts(StockView chests, StockView carried) {

    public StockParts {
        Objects.requireNonNull(chests, "Запас сундуков не может быть null: пустой задают пустым снимком");
        Objects.requireNonNull(carried, "Запас духов не может быть null: пустой задают пустым снимком");
    }

    public StockView total() {
        Map<String, Integer> merged = new HashMap<>(carried.available());
        chests.available().forEach((item, count) -> merged.merge(item, count, Integer::sum));
        return new StockView(merged);
    }

    public StockView free(Map<String, Integer> reserved) {
        Objects.requireNonNull(reserved, "Бронь не может быть null: пустую задают пустой картой");
        Map<String, Integer> free = new HashMap<>(carried.available());
        new StockView(BoardReserve.free(chests.available(), reserved))
                .available()
                .forEach((item, count) -> free.merge(item, count, Integer::sum));
        return new StockView(free);
    }
}
