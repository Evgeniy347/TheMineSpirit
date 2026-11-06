package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardPagingTests {
    private static BoardTile tile(String id, BoardTileGroup group, String spirit) {
        return new BoardTile(id, "BUILD_FENCE", 1, 10, spirit, "PENDING", group, Map.of(), List.of(), 0, 0, 0, 0);
    }

    private static List<BoardTile> board() {
        List<BoardTile> tiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tiles.add(tile("done-" + i, BoardTileGroup.DONE, null));
        }
        for (int i = 0; i < 7; i++) {
            tiles.add(tile("free-" + i, BoardTileGroup.AVAILABLE, i % 2 == 0 ? "Яков" : null));
        }
        return tiles;
    }

    @Test
    void page_AvailableGroup_ReturnsOnlyRequestedColumnPortion() {
        BoardPageQuery query = new BoardPageQuery(BoardTileGroup.AVAILABLE, 2, 3, null, BoardFilter.Spirit.ANY, null);

        BoardPage page = BoardPaging.page(board(), query, 77L);

        assertEquals(
                List.of("free-2", "free-3", "free-4"),
                page.tiles().stream().map(BoardTile::id).toList(),
                "Порция обязана брать только запрошенную колонку и сдвиг offset, а не всю доску");
        assertEquals(7, page.total(), "Счётчик колонки обязан показывать общее число её плиток, а не размер порции");
        assertEquals(7, page.totals().get(BoardTileGroup.AVAILABLE), "Число доступных обязано попасть в totals");
        assertEquals(5, page.totals().get(BoardTileGroup.DONE), "Totals обязаны считать и незапрошенные колонки");
        assertEquals(0, page.totals().get(BoardTileGroup.ACTIVE), "Пустая колонка обязана иметь ноль");
        assertTrue(page.hasMore(), "После смещения 2 при семи плитках обязан остаться хвост ленты");
        assertEquals(77L, page.version(), "Порция обязана нести версию доски, по которой клиент решает об обновлении");
    }

    @Test
    void page_OffsetPastEnd_ReturnsEmptyPortionWithoutTail() {
        BoardPageQuery query = new BoardPageQuery(BoardTileGroup.AVAILABLE, 99, 30, null, BoardFilter.Spirit.ANY, null);

        BoardPage page = BoardPaging.page(board(), query, 1L);

        assertEquals(List.of(), page.tiles(), "Конец ленты обязан отдавать пустую порцию, а не падать");
        assertFalse(page.hasMore(), "За концом ленты хвоста нет: догрузка обязана остановиться");
        assertEquals(7, page.total(), "Даже за концом ленты счётчик колонки обязан остаться общим");
    }

    @Test
    void page_EmptyColumn_ReturnsZeroTotalsAndNoTail() {
        BoardPageQuery query = new BoardPageQuery(BoardTileGroup.ACTIVE, 0, 30, null, BoardFilter.Spirit.ANY, null);

        BoardPage page = BoardPaging.page(board(), query, 3L);

        assertEquals(List.of(), page.tiles(), "У пустой колонки нет плиток: ответ обязан быть пустым списком");
        assertEquals(0, page.total(), "Счётчик пустой колонки обязан быть нулём");
        assertFalse(page.hasMore(), "У пустой колонки нечего догружать");
    }

    @Test
    void page_SpiritFilter_KeepsOnlyNamedSpiritBeforePaging() {
        BoardPageQuery query =
                new BoardPageQuery(BoardTileGroup.AVAILABLE, 0, 30, null, BoardFilter.Spirit.NAMED, "Яков");

        BoardPage page = BoardPaging.page(board(), query, 1L);

        assertEquals(
                List.of("free-0", "free-2", "free-4", "free-6"),
                page.tiles().stream().map(BoardTile::id).toList(),
                "Фильтр духа обязан применяться до нарезки порции, иначе счётчик и страницы разойдутся");
        assertEquals(4, page.total(), "Счётчик колонки обязан считать отфильтрованные плитки");
        assertEquals(4, page.totals().get(BoardTileGroup.AVAILABLE), "Totals обязаны учитывать фильтр духа");
    }

    @Test
    void page_StatusFilterOtherColumn_LeavesRequestedColumnEmpty() {
        BoardPageQuery query =
                new BoardPageQuery(BoardTileGroup.AVAILABLE, 0, 30, BoardTileGroup.DONE, BoardFilter.Spirit.ANY, null);

        BoardPage page = BoardPaging.page(board(), query, 1L);

        assertEquals(List.of(), page.tiles(), "Фильтр статуса на другую колонку обязан оставить запрошенную пустой");
        assertEquals(5, page.totals().get(BoardTileGroup.DONE), "Totals обязаны считать отфильтрованную колонку");
        assertEquals(0, page.totals().get(BoardTileGroup.AVAILABLE), "Колонка вне фильтра статуса обязана обнулиться");
    }

    @Test
    void page_Spirits_ListAllNamesOnBoardForFilterCycle() {
        BoardPageQuery query = new BoardPageQuery(BoardTileGroup.DONE, 0, 30, null, BoardFilter.Spirit.ANY, null);

        BoardPage page = BoardPaging.page(board(), query, 1L);

        assertEquals(
                List.of("Яков"),
                page.spirits(),
                "Список духов обязан собираться по всей доске: иначе кнопка фильтра не переберёт духов за пределами порции");
    }
}
