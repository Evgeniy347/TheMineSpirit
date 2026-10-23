package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardFilterTests {
    private static final List<BoardTile> TILES = List.of(
            tile("a", BoardTileGroup.ACTIVE, "Яков"),
            tile("b", BoardTileGroup.ACTIVE, "Агафья"),
            tile("c", BoardTileGroup.AVAILABLE, null),
            tile("d", BoardTileGroup.DONE, "Яков"));

    private static BoardTile tile(String id, BoardTileGroup group, String spirit) {
        return new BoardTile(id, "FISH", 1, 10, spirit, "PENDING", group, Map.of(), 0);
    }

    @Test
    void apply_GroupAndSpirit_KeepsOnlyMatching() {
        BoardFilter filter = new BoardFilter(BoardTileGroup.ACTIVE, BoardFilter.Spirit.NAMED, "Яков");

        assertEquals(
                List.of("a"),
                filter.apply(TILES).stream().map(BoardTile::id).toList(),
                "Фильтр обязан оставить только активные задания Якова");
    }

    @Test
    void apply_Nobody_KeepsUnassigned() {
        BoardFilter filter = new BoardFilter(null, BoardFilter.Spirit.NOBODY, null);

        assertEquals(
                List.of("c"),
                filter.apply(TILES).stream().map(BoardTile::id).toList(),
                "Режим «…» оставляет только невыданные задания");
    }

    @Test
    void nextSpirit_Cycle_AnyNobodyNamesAlphabeticallyAny() {
        BoardFilter filter = BoardFilter.ALL;
        filter = filter.nextSpirit(TILES);
        assertEquals(BoardFilter.Spirit.NOBODY, filter.spirit(), "После «все» идёт «…»");
        filter = filter.nextSpirit(TILES);
        assertEquals("Агафья", filter.spiritName(), "После «…» идёт первый дух по алфавиту");
        filter = filter.nextSpirit(TILES);
        assertEquals("Яков", filter.spiritName(), "Духи перебираются по алфавиту");
        filter = filter.nextSpirit(TILES);
        assertEquals(BoardFilter.ALL, filter, "После последнего духа фильтр возвращается к «все»");
    }

    @Test
    void nextGroup_Cycle_ReturnsToAll() {
        BoardFilter filter = BoardFilter.ALL;
        for (BoardTileGroup expected : BoardTileGroup.values()) {
            filter = filter.nextGroup();
            assertEquals(expected, filter.group(), "Группы перебираются в порядке показа");
        }
        assertNull(filter.nextGroup().group(), "После последней группы фильтр возвращается к «все»");
    }

    @Test
    void constructor_NamedWithoutName_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardFilter(null, BoardFilter.Spirit.NAMED, " "),
                "Фильтр по духу без имени — ошибка вызова");
    }
}
