package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BoardFeed {
    public static final int PAGE_SIZE = 30;

    private final int pageSize;
    private final Map<BoardTileGroup, Column> columns = new EnumMap<>(BoardTileGroup.class);
    private BoardFilter filter = BoardFilter.ALL;
    private List<String> spirits = List.of();
    private boolean seen;

    public BoardFeed() {
        this(PAGE_SIZE);
    }

    public BoardFeed(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Размер порции " + pageSize + " меньше 1: лента не сдвинется с места");
        }
        this.pageSize = pageSize;
        for (BoardTileGroup group : BoardTileGroup.values()) {
            columns.put(group, new Column());
        }
    }

    public BoardFilter filter() {
        return filter;
    }

    public void filter(BoardFilter next) {
        this.filter = Objects.requireNonNull(next, "Фильтр не может быть null: «все» задают BoardFilter.ALL");
        reset();
    }

    public List<BoardTile> tiles(BoardTileGroup group) {
        return List.copyOf(state(group).tiles);
    }

    public int total(BoardTileGroup group) {
        return state(group).total;
    }

    public boolean complete(BoardTileGroup group) {
        return state(group).complete;
    }

    public boolean pending(BoardTileGroup group) {
        return state(group).pending;
    }

    public boolean seen() {
        return seen;
    }

    public List<String> spirits() {
        return spirits;
    }

    public List<BoardColumns.BoardColumn> columns() {
        List<BoardColumns.BoardColumn> result = new ArrayList<>(BoardTileGroup.values().length);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            result.add(new BoardColumns.BoardColumn(group, state(group).tiles));
        }
        return List.copyOf(result);
    }

    public @Nullable BoardPageQuery query(BoardTileGroup group, boolean grow) {
        Column column = state(group);
        if (grow && column.complete) {
            return null;
        }
        int offset = grow ? column.tiles.size() : 0;
        int limit = grow ? pageSize : Math.min(BoardPageQuery.MAX_LIMIT, Math.max(column.tiles.size(), pageSize));
        return new BoardPageQuery(group, offset, limit, filter.group(), filter.spirit(), filter.spiritName());
    }

    public void markPending(BoardTileGroup group) {
        state(group).pending = true;
    }

    public void apply(BoardPage page) {
        Objects.requireNonNull(page, "Порция не может быть null: клиент разбирает ответ сервера");
        Column column = state(page.group());
        column.pending = false;
        if (page.offset() == 0) {
            column.tiles.clear();
        }
        int prefix = Math.min(page.offset(), column.tiles.size());
        List<BoardTile> merged = new ArrayList<>(column.tiles.subList(0, prefix));
        Map<String, Boolean> known = new LinkedHashMap<>();
        for (BoardTile tile : merged) {
            known.put(tile.id(), Boolean.TRUE);
        }
        for (BoardTile tile : page.tiles()) {
            if (known.putIfAbsent(tile.id(), Boolean.TRUE) == null) {
                merged.add(tile);
            }
        }
        column.tiles.clear();
        int keep = Math.min(merged.size(), Math.max(page.total(), 0));
        column.tiles.addAll(merged.subList(0, keep));
        column.total = page.total();
        column.complete = column.tiles.size() >= column.total;
        spirits = page.spirits();
        seen = true;
    }

    public void reset() {
        for (Column column : columns.values()) {
            column.tiles.clear();
            column.total = 0;
            column.complete = false;
            column.pending = false;
        }
        seen = false;
    }

    private Column state(BoardTileGroup group) {
        Objects.requireNonNull(group, "Колонка не может быть null: у каждой группы своя лента");
        return Objects.requireNonNull(columns.get(group), "У группы " + group + " обязана быть лента");
    }

    private static final class Column {
        private final List<BoardTile> tiles = new ArrayList<>();
        private int total;
        private boolean complete;
        private boolean pending;
    }
}
