package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchCountTests {
    private static final List<BoardTile> TILES = List.of(tile("house", "family house"), tile("mine", "shaft step"));

    @Test
    void count_EmptyQuery_FindsAll() {
        SearchCount count = SearchCount.of(TILES, "", SearchCountTests::haystack);
        assertEquals(
                2,
                count.found(),
                "Пустой запрос обязан находить все загруженные плитки: иначе счётчик рядом с поиском пугает нулём без причины. При смене count_EmptyQuery_FindsAll проверь это утверждение.");
        assertFalse(
                count.empty(),
                "При пустом запросе пустого результата нет: иначе экран покажет «ничего не найдено» на полной доске. При смене count_EmptyQuery_FindsAll проверь это утверждение.");
    }

    @Test
    void count_WordInDetails_FindsMatch() {
        SearchCount count = SearchCount.of(TILES, "shaft", SearchCountTests::haystack);
        assertEquals(
                1,
                count.found(),
                "Слово из подробностей задания обязано находить плитку: иначе поиск по описанию расходится с колонками. При смене count_WordInDetails_FindsMatch проверь это утверждение.");
    }

    @Test
    void count_NoMatch_ReportsEmpty() {
        SearchCount count = SearchCount.of(TILES, "дракон", SearchCountTests::haystack);
        assertTrue(
                count.empty(),
                "Без совпадений счётчик обязан сообщать пустоту: иначе экран рисует полный список вместо «Нет заданий под фильтр» и кнопки сброса. При смене count_NoMatch_ReportsEmpty проверь это утверждение.");
    }

    @Test
    void count_LoadedZero_KeepsZero() {
        SearchCount count = SearchCount.of(List.of(), "дом", SearchCountTests::haystack);
        assertEquals(
                0,
                count.loaded(),
                "Без загруженных страниц счётчик обязан показывать ноль загруженных: иначе индикатор обещает плитки, которых нет. При смене count_LoadedZero_KeepsZero проверь это утверждение.");
    }

    @Test
    void constructor_FoundAboveLoaded_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchCount(3, 2),
                "Найденное больше загруженного обязано падать: иначе ошибка счёта покажет «найдено 3 из 2» и подорвёт доверие к поиску. При смене constructor_FoundAboveLoaded_IsRejected проверь это утверждение.");
    }

    private static String haystack(BoardTile tile) {
        return tile.id() + " " + String.join(" ", tile.details());
    }

    private static BoardTile tile(String id, String details) {
        return new BoardTile(
                id,
                "build_house",
                1,
                1,
                null,
                "waiting_resources",
                BoardTileGroup.ACTIVE,
                Map.of(),
                List.of(),
                0,
                0,
                0,
                0,
                null,
                0,
                List.of(details));
    }
}
