package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.api.BoardTileGroup;
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

        assertEquals(
                2,
                feed.tiles(BoardTileGroup.AVAILABLE).size(),
                "Первая страница обязана лечь в колонку"
                        + " Иначе тест firstPage_EmptyFeed_LoadsPortionAndKeepsTotal перестанет ловить эту поломку. При смене firstPage_EmptyFeed_LoadsPortionAndKeepsTotal проверь это утверждение.");
        assertEquals(
                50,
                feed.total(BoardTileGroup.AVAILABLE),
                "Счётчик колонки обязан показывать общее число заданий, а не число загруженных"
                        + " Иначе тест firstPage_EmptyFeed_LoadsPortionAndKeepsTotal перестанет ловить эту поломку. При смене firstPage_EmptyFeed_LoadsPortionAndKeepsTotal проверь это утверждение.");
        assertFalse(
                feed.complete(BoardTileGroup.AVAILABLE),
                "При 50 заданиях и двух загруженных лента не закончена"
                        + " Иначе тест firstPage_EmptyFeed_LoadsPortionAndKeepsTotal перестанет ловить эту поломку. При смене firstPage_EmptyFeed_LoadsPortionAndKeepsTotal проверь это утверждение.");
        assertTrue(
                feed.seen(),
                "После ответа клиент уже знает доску: показывать «загрузка» нельзя"
                        + " Иначе тест firstPage_EmptyFeed_LoadsPortionAndKeepsTotal перестанет ловить эту поломку. При смене firstPage_EmptyFeed_LoadsPortionAndKeepsTotal проверь это утверждение.");
    }

    @Test
    void nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b", "c"));

        feed.apply(page(BoardTileGroup.AVAILABLE, 3, 5, "d", "e"));

        assertEquals(
                List.of("a", "b", "c", "d", "e"),
                feed.tiles(BoardTileGroup.AVAILABLE).stream().map(BoardTile::id).toList(),
                "Вторая порция обязана дописываться в конец ленты, а не заменять первую"
                        + " Иначе тест nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles перестанет ловить эту поломку. При смене nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles проверь это утверждение.");
        assertTrue(
                feed.complete(BoardTileGroup.AVAILABLE),
                "Когда загружено всё, лента обязана считаться законченной"
                        + " Иначе тест nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles перестанет ловить эту поломку. При смене nextPage_AfterFirst_AppendsWithoutLosingEarlierTiles проверь это утверждение.");
    }

    @Test
    void apply_ChangedBoardBetweenPages_DoesNotDuplicateLoadedTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 6, "a", "b", "c"));

        feed.apply(page(BoardTileGroup.AVAILABLE, 3, 6, "c", "d", "e"));

        assertEquals(
                List.of("a", "b", "c", "d", "e"),
                feed.tiles(BoardTileGroup.AVAILABLE).stream().map(BoardTile::id).toList(),
                "Сдвиг доски между порциями не имеет права показать плитку дважды — иначе лента врёт о заданиях"
                        + " При смене apply_ChangedBoardBetweenPages_DoesNotDuplicateLoadedTiles проверь это утверждение.");
    }

    @Test
    void refresh_LoadedPrefix_ReplacesStaleTiles() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.ACTIVE, 0, 2, "old", "keep"));

        feed.apply(page(BoardTileGroup.ACTIVE, 0, 2, "new", "keep"));

        assertEquals(
                List.of("new", "keep"),
                feed.tiles(BoardTileGroup.ACTIVE).stream().map(BoardTile::id).toList(),
                "Обновление загруженной порции обязано заменять её, а не дописывать поверх"
                        + " Иначе тест refresh_LoadedPrefix_ReplacesStaleTiles перестанет ловить эту поломку. При смене refresh_LoadedPrefix_ReplacesStaleTiles проверь это утверждение.");
    }

    @Test
    void filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b"));

        feed.filter(new BoardFilter(BoardTileGroup.ACTIVE, BoardFilter.Spirit.NAMED, "Яков"));

        assertEquals(
                List.of(),
                feed.tiles(BoardTileGroup.AVAILABLE),
                "Смена фильтра обязана сбросить старые порции"
                        + " Иначе тест filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery перестанет ловить эту поломку. При смене filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery проверь это утверждение.");
        BoardPageQuery query = feed.query(BoardTileGroup.AVAILABLE, false);
        assertEquals(
                BoardTileGroup.ACTIVE,
                query.status(),
                "Query обязан нести фильтр статуса на сервер"
                        + " Иначе тест filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery перестанет ловить эту поломку. При смене filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery проверь это утверждение.");
        assertEquals(
                "Яков",
                query.spiritName(),
                "Query обязан нести имя духа на сервер"
                        + " Иначе тест filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery перестанет ловить эту поломку. При смене filter_Changed_ClearsLoadedTilesAndCarriesFilterIntoQuery проверь это утверждение.");
    }

    @Test
    void query_AfterFirstPage_NextPageStartsAtLoadedCount() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 5, "a", "b"));

        BoardPageQuery next = feed.query(BoardTileGroup.AVAILABLE, true);
        BoardPageQuery refresh = feed.query(BoardTileGroup.AVAILABLE, false);

        assertEquals(
                2,
                next.offset(),
                "Догрузка обязана начинаться с числа уже загруженных плиток"
                        + " Иначе тест query_AfterFirstPage_NextPageStartsAtLoadedCount перестанет ловить эту поломку. При смене query_AfterFirstPage_NextPageStartsAtLoadedCount проверь это утверждение.");
        assertEquals(
                BoardFeed.PAGE_SIZE,
                next.limit(),
                "Догрузка обязана просить очередную порцию, а не всю доску"
                        + " Иначе тест query_AfterFirstPage_NextPageStartsAtLoadedCount перестанет ловить эту поломку. При смене query_AfterFirstPage_NextPageStartsAtLoadedCount проверь это утверждение.");
        assertEquals(
                0,
                refresh.offset(),
                "Обновление раз в секунду обязано перечитывать только загруженную порцию"
                        + " Иначе тест query_AfterFirstPage_NextPageStartsAtLoadedCount перестанет ловить эту поломку. При смене query_AfterFirstPage_NextPageStartsAtLoadedCount проверь это утверждение.");
        assertEquals(
                BoardFeed.PAGE_SIZE,
                refresh.limit(),
                "Пока загружено меньше порции, обновление обязано просить хотя бы первую порцию"
                        + " Иначе тест query_AfterFirstPage_NextPageStartsAtLoadedCount перестанет ловить эту поломку. При смене query_AfterFirstPage_NextPageStartsAtLoadedCount проверь это утверждение.");
    }

    @Test
    void query_FinishedFeed_ReturnsNoNextPage() {
        BoardFeed feed = new BoardFeed();
        feed.apply(page(BoardTileGroup.AVAILABLE, 0, 2, "a", "b"));

        assertNull(
                feed.query(BoardTileGroup.AVAILABLE, true),
                "У законченной ленты нет следующей страницы"
                        + " Иначе тест query_FinishedFeed_ReturnsNoNextPage перестанет ловить эту поломку. При смене query_FinishedFeed_ReturnsNoNextPage проверь это утверждение.");
    }

    @Test
    void emptyColumn_ServerSaidZero_IsSeenAndFinished() {
        BoardFeed feed = new BoardFeed();

        feed.apply(page(BoardTileGroup.ACTIVE, 0, 0));

        assertTrue(
                feed.complete(BoardTileGroup.ACTIVE),
                "Пустая колонка обязана считаться законченной сразу"
                        + " Иначе тест emptyColumn_ServerSaidZero_IsSeenAndFinished перестанет ловить эту поломку. При смене emptyColumn_ServerSaidZero_IsSeenAndFinished проверь это утверждение.");
        assertTrue(
                feed.seen(),
                "Пустой ответ всё равно означает, что доска загружена"
                        + " Иначе тест emptyColumn_ServerSaidZero_IsSeenAndFinished перестанет ловить эту поломку. При смене emptyColumn_ServerSaidZero_IsSeenAndFinished проверь это утверждение.");
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
                "Список духов из ответа обязан использоваться кнопкой фильтра: он полнее загруженной порции"
                        + " Иначе тест spirits_FromServerPage_ReachFilterCycle перестанет ловить эту поломку. При смене spirits_FromServerPage_ReachFilterCycle проверь это утверждение.");
    }
}
