package com.theminespirit.core.rest;

import com.theminespirit.core.ui.BoardTileGroup;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BoardListing(
        List<BoardTask> tasks,
        int total,
        int offset,
        int limit,
        Map<BoardTileGroup, Integer> groups,
        @Nullable String nextCursor,
        long version) {
    public BoardListing {
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks не может быть null: пустую страницу задают списком"));
        Objects.requireNonNull(groups, "groups не может быть null: клиент показывает счётчики колонок");
        if (total < 0 || offset < 0 || limit < 1) {
            throw new IllegalArgumentException("Границы страницы доски " + total + "/" + offset + "/" + limit
                    + " невозможны: total и offset неотрицательны, limit от 1");
        }
        Map<BoardTileGroup, Integer> complete = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            int count = groups.getOrDefault(group, 0);
            if (count < 0) {
                throw new IllegalArgumentException(
                        "Счётчик колонки " + group + " равен " + count + ": заданий не бывает меньше нуля");
            }
            complete.put(group, count);
        }
        groups = Map.copyOf(complete);
    }
}
