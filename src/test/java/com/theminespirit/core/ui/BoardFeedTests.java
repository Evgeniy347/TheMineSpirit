package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardFeedTests {
    private static BoardTile tile(String id, BoardTileGroup group) {
        return new BoardTile(id, "BUILD_FENCE", 1, 10, null, "PENDING", group, Map.of(), List.of(), 0, 0, 0, 0);
    }

    private static BoardPage page(BoardTileGroup group, int offset, int total, String... ids) {
        Map<BoardTileGroup, Integer> totals = new LinkedHashMap<>();
        for (BoardTileGroup value : BoardTileGroup.values()) {
            totals.put(value, value == group ? total : 0);
        }
        List<BoardTile> tiles = new ArrayList<>();
        for (String id : ids) {
            tiles.add(tile(id, group));
        }
        return new BoardPage(group, offset, tiles, totals, List.of(), 1L, offset + tiles.size() < total);
    }

    @Test
    void firstPage_EmptyFeed_LoadsPortionAndKeepsTotal() {
        BoardFeed feed = new BoardFeed();

        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 50, "a", "b"));

        assertEquals(2, feed.tiles(BoardTileGroup.AVAILABLE).size(), "Первая страница обязана лечь в колонку");
        assertEquals(
                50,
                feed.total(BoardTileGroup.AVAILABLE),
                "Счётчик колонки обязан показывать общее число заданий, а не число загруженных");
        assertFalse(feed.complete(BoardTileGroup.AVAILABLE), "При 50 заданиях и двух загруженных лента не закончена");
        assertTrue(feed.seen(), "После ответа клиент уже знает доску: показывать «загрузка» нельзя");
    }

    @Test
    void nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b", "c"));

        feed.apply(page(BoardTileGroup.AVAILABLE, 3, 5, "d", "e"));

        assertEquals(
                List.of("a", "b", "c", "d", "e"),
                feed.tiles(BoardTileGroup.AVAILABLE).stream().map(BoardTile::id).toList(),
                "Вторая порция обязана дописываться в конец ленты, а не заменять первую");
        assertTrue(feed.complete(BoardTileGroup.AVAILABLE), "Когда загружено всё, лента обязана считаться законченной");
    }

    @Test
    void apply_ChangedBoardBetweenPages_DoesNotDuplicateLoadedTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 6, "a", "b", "c"));

        feed.apply(page(BoardTileGroup.AVAILABLE, 3, 6, "c", "d", "e"));

        assertEquals(
                List.of("a", "b", "c", "d", "e"),
                feed.tiles(BoardTileGroup.AVAILABLE).stream().map(BoardTile::id).toList(),
                "Сдвиг доски между порциями не имеет права показать плитку дважды — иначе лента врёт о заданиях");
    }

    @Test
    void refresh_LoadedPrefix_ReplacesStaleTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.ACTIVE, 0, 2, "old", "keep"));

        feed.apply(page(BoardTileGroup.ACTIVE, 0, 2, "new", "keep"));

        assertEquals(
                List.of("new", "keep"),
                feed.tiles(BoardTileGroup.ACTIVE).stream().map(BoardTile::id).toList(),
                "Обновление загруженной порции обязано заменять её, а не дописывать поверх");
    }

    @Test
    void filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b"));

        feed.filter(new BoardFilter(BoardTileGroup.ACTIVE, BoardFilter.Spirit.NAMED, "Яков"));

        assertEquals(List.of(), feed.tiles(BoardTileGroup.AVAILABLE), "Смена фильтра обязана сбросить старые порции");
        BoardPageQuery query = feed.query(BoardTileGroup.AVAILABLE, false);
        assertEquals(BoardTileGroup.ACTIVE, query.status(), "Query обязан нести фильтр статуса на сервер");
        assertEquals("Яков", query.spiritName(), "Query обязан нести имя духа на сервер");
    }

    @Test
    void query_AfterFirstPage_NextPageStartsAtLoadedCount() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b"));

        BoardPageQuery next = feed.query(BoardTileGroup.AVAILABLE, true);
        BoardPageQuery refresh = feed.query(BoardTileGroup.AVAILABLE, false);

        assertEquals(2, next.offset(), "Догрузка обязана начинаться с числа уже загруженных плиток");
        assertEquals(BoardFeed.PAGE_SIZE, next.limit(), "Догрузка обязана просить очередную порцию, а не всю доску");
        assertEquals(0, refresh.offset(), "Обновление раз в секунду обязано перечитывать только загруженную порцию");
        assertEquals(
                BoardFeed.PAGE_SIZE,
                refresh.limit(),
                "Пока загружено меньше порции, обновление обязано просить хотя бы первую порцию");
    }

    @Test
    void query_FinishedFeed_ReturnsNoNextPage() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 2, "a", "b"));

        assertNull(feed.query(BoardTileGroup.AVAILABLE, true), "У законченной ленты нет следующей страницы");
    }

    @Test
    void emptyColumn_ServerSaidZero_IsSeenAndFinished() {
        BoardFeed feed = new BoardFeed();

        feed.apply(page(BoardTileGroup.ACTIVE, 0, 0));

        assertTrue(feed.complete(BoardTileGroup.ACTIVE), "Пустая колонка обязана считаться законченной сразу");
        assertTrue(feed.seen(), "Пустой ответ всё равно означает, что доска загружена");
    }

    @Test
    void spirits_FromServerPage_ReachFilterCycle() {
        BoardFeed feed = new BoardFeed();

        feed.apply(new BoardPage(
                BoardTileGroup.AVAILABLE,
                0,
                List.of(tile("a", BoardTileGroup.AVAILABLE)),
                Map.of(),
                List.of("Агафья", "Яков"),
                1L,
                false));

        assertEquals(
                List.of("Агафья", "Яков"),
                feed.spirits(),
                "Список духов из ответа обязан использоваться кнопкой фильтра: он полнее загруженной порции");
    }
}
