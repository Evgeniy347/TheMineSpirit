package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardColumnsTests {
    private static BoardTile tile(String id, BoardTileGroup group) {
        return new BoardTile(id, "FISH", 1, 10, null, "PENDING", group, Map.of(), List.of(), 0, 0, 0, 0);
    }

    @Test
    void split_FourGroups_ReturnsColumnsInShowOrderAndKeepsTiles() {
        List<BoardTile> tiles = List.of(
                tile("done", BoardTileGroup.DONE),
                tile("active", BoardTileGroup.ACTIVE),
                tile("waiting", BoardTileGroup.WAITING),
                tile("free-1", BoardTileGroup.AVAILABLE),
                tile("free-2", BoardTileGroup.AVAILABLE));

        List<BoardColumns.BoardColumn> columns = BoardColumns.split(tiles);

        assertEquals(
                List.of(BoardTileGroup.DONE, BoardTileGroup.ACTIVE, BoardTileGroup.WAITING, BoardTileGroup.AVAILABLE),
                columns.stream().map(BoardColumns.BoardColumn::group).toList(),
                "Колонки обязаны идти в порядке показа: завершённые, активные, ждущие, доступные");
        assertEquals(
                List.of("free-1", "free-2"),
                columns.get(3).tiles().stream().map(BoardTile::id).toList(),
                "Раскладка по колонкам обязана сохранить порядок плиток внутри группы");
    }

    @Test
    void split_EmptyBoard_ReturnsOneEmptyColumnPerGroup() {
        List<BoardColumns.BoardColumn> columns = BoardColumns.split(List.of());

        assertEquals(
                BoardTileGroup.values().length,
                columns.size(),
                "Пустая доска всё равно показывает по колонке на каждую группу");
        assertEquals(
                0,
                columns.stream().mapToInt(column -> column.tiles().size()).sum(),
                "У пустой доски ни в одной колонке нет плиток");
    }
}
