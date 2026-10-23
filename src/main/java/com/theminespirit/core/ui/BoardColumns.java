package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Колонки экрана доски: одна колонка на группу, каждая со своим списком плиток. */
public final class BoardColumns {
    private BoardColumns() {}

    public static List<BoardColumn> split(List<BoardTile> tiles) {
        Objects.requireNonNull(tiles, "tiles не может быть null: пустая доска — пустой список");
        Map<BoardTileGroup, List<BoardTile>> grouped = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            grouped.put(group, new ArrayList<>());
        }
        for (BoardTile tile : tiles) {
            Objects.requireNonNull(tile, "плитка доски не может быть null: её раскладывают по группе");
            Objects.requireNonNull(grouped.get(tile.group()), "группа плитки обязана иметь колонку")
                    .add(tile);
        }
        List<BoardColumn> columns = new ArrayList<>(BoardTileGroup.values().length);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            columns.add(
                    new BoardColumn(group, Objects.requireNonNull(grouped.get(group), "группа обязана иметь колонку")));
        }
        return List.copyOf(columns);
    }

    public record BoardColumn(BoardTileGroup group, List<BoardTile> tiles) {
        public BoardColumn {
            Objects.requireNonNull(group, "Группа колонки не может быть null: по ней колонка подписана");
            tiles = List.copyOf(
                    Objects.requireNonNull(tiles, "Плитки колонки не могут быть null: пустую задают списком"));
        }
    }
}
