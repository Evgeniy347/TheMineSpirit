package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class BoardPaging {
    private BoardPaging() {}

    public static BoardPage page(List<BoardTile> tiles, BoardPageQuery query, long version) {
        Objects.requireNonNull(tiles, "tiles не может быть null: пустая доска — пустой список");
        Objects.requireNonNull(query, "query не может быть null: порцию собирают по запросу");
        BoardFilter filter = query.filter();
        Map<BoardTileGroup, Integer> totals = emptyTotals();
        Map<BoardTileGroup, List<BoardTile>> groups = emptyGroups();
        Set<String> spirits = new TreeSet<>();
        for (BoardTile tile : tiles) {
            if (tile.spirit() != null) {
                spirits.add(tile.spirit());
            }
            if (filter.accepts(tile)) {
                totals.merge(tile.group(), 1, Integer::sum);
                Objects.requireNonNull(groups.get(tile.group()), "у группы обязана быть колонка")
                        .add(tile);
            }
        }
        List<BoardTile> selected = Objects.requireNonNull(groups.get(query.group()), "у группы обязана быть колонка");
        int total = selected.size();
        int from = Math.min(query.offset(), total);
        int to = Math.min(from + query.limit(), total);
        return new BoardPage(
                query.group(), from, selected.subList(from, to), totals, List.copyOf(spirits), version, to < total);
    }

    public static BoardPage pageRows(
            List<BoardSnapshot.Row> rows,
            BoardPageQuery query,
            Function<String, @Nullable String> spiritNames,
            Map<String, List<String>> targetDetails,
            long version) {
        Objects.requireNonNull(rows, "rows не может быть null: пустая доска — пустой список строк");
        Objects.requireNonNull(query, "query не может быть null: порцию собирают по запросу");
        Objects.requireNonNull(spiritNames, "spiritNames не может быть null: без него не отфильтровать по духу");
        Objects.requireNonNull(
                targetDetails, "targetDetails не может быть null: без целей задание задают пустой картой");
        BoardFilter filter = query.filter();
        Map<BoardTileGroup, Integer> totals = emptyTotals();
        List<BoardSnapshot.Row> selected = new ArrayList<>();
        Set<String> spirits = new TreeSet<>();
        for (BoardSnapshot.Row row : rows) {
            String spirit = BoardSnapshot.nameOf(row.task().assignee(), spiritNames);
            if (spirit != null) {
                spirits.add(spirit);
            }
            if (!filter.accepts(row.group(), spirit)) {
                continue;
            }
            totals.merge(row.group(), 1, Integer::sum);
            if (row.group() == query.group()) {
                selected.add(row);
            }
        }
        int total = selected.size();
        int from = Math.min(query.offset(), total);
        int to = Math.min(from + query.limit(), total);
        List<BoardTile> tiles = new ArrayList<>(to - from);
        for (BoardSnapshot.Row row : selected.subList(from, to)) {
            tiles.add(BoardSnapshot.tile(row, spiritNames, targetDetails));
        }
        return new BoardPage(query.group(), from, tiles, totals, List.copyOf(spirits), version, to < total);
    }

    private static Map<BoardTileGroup, Integer> emptyTotals() {
        Map<BoardTileGroup, Integer> totals = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            totals.put(group, 0);
        }
        return totals;
    }

    private static Map<BoardTileGroup, List<BoardTile>> emptyGroups() {
        Map<BoardTileGroup, List<BoardTile>> groups = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            groups.put(group, new ArrayList<>());
        }
        return groups;
    }
}
