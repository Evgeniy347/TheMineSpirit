package com.theminespirit.core.ui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BoardPage(
        BoardTileGroup group,
        int offset,
        List<BoardTile> tiles,
        Map<BoardTileGroup, Integer> totals,
        List<String> spirits,
        long version,
        boolean hasMore) {

    public BoardPage {
        Objects.requireNonNull(group, "Колонка ответа не может быть null: порция адресована одной колонке");
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset = " + offset + " отрицателен: порция не может начинаться до начала ленты");
        }
        tiles = List.copyOf(Objects.requireNonNull(tiles, "Плитки порции не могут быть null: пустую задают списком"));
        Objects.requireNonNull(totals, "Счётчики колонок не могут быть null: клиент показывает общее число заданий");
        Map<BoardTileGroup, Integer> complete = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup value : BoardTileGroup.values()) {
            int count = totals.getOrDefault(value, 0);
            if (count < 0) {
                throw new IllegalArgumentException(
                        "Счётчик колонки " + value + " равен " + count + ": заданий не бывает меньше нуля");
            }
            complete.put(value, count);
        }
        totals = Map.copyOf(complete);
        spirits =
                List.copyOf(Objects.requireNonNull(spirits, "Список духов не может быть null: пустой задают списком"));
    }

    public int total() {
        return totals.getOrDefault(group, 0);
    }
}
